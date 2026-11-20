package com.cropdisease.service;

import com.cropdisease.model.Disease;
import com.cropdisease.repository.DiseaseRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Turns leaf features into a diagnosis.
 *
 * Score for every candidate disease of the chosen crop (+ nutrient disorders):
 *   visual = 0.6 x colour-signature similarity (cosine) + 0.4 x spread-pattern match
 *   final  = visual (photo only)  or  0.7 x visual + 0.3 x symptom match (if the farmer ticked symptoms)
 * Scores are turned into probabilities with a softmax, then adjusted for image quality.
 */
@Service
public class DiagnosisService {

    public static final double HEALTHY_DAMAGE = 0.035;

    private final DiseaseRepository diseases;

    public DiagnosisService(DiseaseRepository diseases) {
        this.diseases = diseases;
    }

    public record Candidate(String code, String name, String cropCode, String type, String severity,
                            double probability, double colourMatch, double patternMatch, Double symptomMatch) { }

    public record Diagnosis(String status, String title, String diseaseCode, double confidence,
                            double severityPct, String stage, int healthScore, String urgency,
                            String dominantPattern, Map<String, Double> lesionMix,
                            List<Candidate> candidates, List<String> observations, List<String> qualityWarnings) { }

    // ------------------------------------------------------------------
    // Photo diagnosis
    // ------------------------------------------------------------------

    public Diagnosis diagnose(LeafAnalyzer.Features f, String cropCode, Set<String> symptoms) {
        List<Disease> pool = pool(cropCode);
        double D = f.damage();

        Map<String, Double> mix = new LinkedHashMap<>();
        double[] v = lesionVector(f);
        mix.put("chlorosis", pct(v[0]));
        mix.put("necrosis", pct(v[1]));
        mix.put("dark lesion", pct(v[2]));
        mix.put("powdery / mildew", pct(v[3]));
        mix.put("rust pustule", pct(v[4]));

        Map<String, Double> patterns = patternScores(f, v);
        String dominantPattern = patterns.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("SPOTS");

        // score each disease
        List<double[]> scored = new ArrayList<>();
        List<Disease> order = new ArrayList<>();
        for (Disease d : pool) {
            double colour = cosine(v, new double[]{d.getSigYellow(), d.getSigBrown(), d.getSigDark(), d.getSigWhite(), d.getSigRust()});
            double pattern = patterns.getOrDefault(d.getPattern(), 0.05);
            double visual = 0.6 * colour + 0.4 * pattern;
            Double sym = null;
            double total = visual;
            if (symptoms != null && !symptoms.isEmpty()) {
                sym = symptomMatch(d, symptoms);
                total = 0.7 * visual + 0.3 * sym;
            }
            if ("general".equals(d.getCropCode()) && cropCode != null && !cropCode.isBlank()) total *= 0.92; // prefer crop-specific
            scored.add(new double[]{total, colour, pattern, sym == null ? -1 : sym});
            order.add(d);
        }

        // softmax
        double max = scored.stream().mapToDouble(s -> s[0]).max().orElse(0);
        double sum = 0;
        double[] e = new double[scored.size()];
        for (int i = 0; i < e.length; i++) { e[i] = Math.exp((scored.get(i)[0] - max) / 0.06); sum += e[i]; }

        List<Candidate> cands = new ArrayList<>();
        for (int i = 0; i < e.length; i++) {
            Disease d = order.get(i);
            double[] s = scored.get(i);
            cands.add(new Candidate(d.getCode(), d.getName(), d.getCropCode(), d.getType(), d.getSeverity(),
                    round(e[i] / sum), round(s[1]), round(s[2]), s[3] < 0 ? null : round(s[3])));
        }
        cands.sort((a, b) -> Double.compare(b.probability(), a.probability()));
        List<Candidate> top = cands.subList(0, Math.min(5, cands.size()));

        // quality
        List<String> warnings = new ArrayList<>();
        double quality = 1.0;
        if (!f.leafFound()) { warnings.add("A leaf could not be clearly separated from the background. Place one leaf on a plain background."); quality *= 0.75; }
        if (f.sharpness() < 25) { warnings.add("The photo looks blurry. Hold the phone steady and tap to focus on the leaf."); quality *= 0.85; }
        if (f.brightness() < 0.22) { warnings.add("The photo is too dark. Take it in daylight or shade, without flash."); quality *= 0.85; }
        if (f.brightness() > 0.88) { warnings.add("The photo is over-exposed. Avoid direct harsh sunlight on the leaf."); quality *= 0.9; }
        if (f.leafFound() && f.leafCoverage() < 0.10) { warnings.add("The leaf is small in the frame. Move closer so the leaf fills most of the photo."); quality *= 0.9; }

        double severityPct = Math.round(D * 1000) / 10.0;
        String stage = stage(D);
        int health = (int) Math.max(0, Math.round(100 - Math.min(100, D * 100 * 1.6)));

        List<String> obs = observations(f, mix, dominantPattern);

        // Rust pustules / mildew colonies are tiny but very specific: a lower damage threshold applies
        boolean specific = (v[4] > 0.35 || v[3] > 0.35) && f.spotCount() >= 8;
        double healthyLimit = specific ? 0.012 : HEALTHY_DAMAGE;
        if (D < healthyLimit || top.isEmpty()) {
            double conf = Math.min(0.95, (0.80 + (healthyLimit - D) / healthyLimit * 0.12) * quality);
            List<Candidate> near = D < 0.01 ? List.of() : top.subList(0, Math.min(3, top.size()));
            return new Diagnosis("HEALTHY", "Healthy leaf", null, round(conf), severityPct, "Healthy", health,
                    "No treatment needed - keep monitoring weekly", dominantPattern, mix, near, obs, warnings);
        }
        if (stage.equals("Healthy")) stage = "Early";

        Candidate best = top.get(0);
        double bestScore = scored.get(order.indexOf(order.stream().filter(d -> d.getCode().equals(best.code())).findFirst().orElseThrow()))[0];
        double conf = (0.55 * best.probability() + 0.45 * bestScore) * quality;
        if (D < 0.07) conf *= 0.85;                    // very early symptoms are harder to tell apart
        conf = Math.max(0.2, Math.min(0.94, conf));

        boolean uncertain = conf < 0.45;
        String status = uncertain ? "UNCERTAIN" : "DISEASED";
        String title = uncertain ? "Possible " + best.name() : best.name();
        String urgency = urgency(best.severity(), stage, uncertain);

        return new Diagnosis(status, title, best.code(), round(conf), severityPct, stage, health, urgency,
                dominantPattern, mix, top, obs, warnings);
    }

    // ------------------------------------------------------------------
    // Symptom checker (no photo)
    // ------------------------------------------------------------------

    public List<Candidate> bySymptoms(String cropCode, Set<String> symptoms) {
        if (symptoms == null || symptoms.isEmpty()) throw new IllegalArgumentException("Tick at least one symptom.");
        List<Candidate> out = new ArrayList<>();
        for (Disease d : pool(cropCode)) {
            double m = symptomMatch(d, symptoms);
            if (m <= 0) continue;
            if ("general".equals(d.getCropCode()) && cropCode != null && !cropCode.isBlank()) m *= 0.9;
            out.add(new Candidate(d.getCode(), d.getName(), d.getCropCode(), d.getType(), d.getSeverity(),
                    round(m), 0, 0, round(m)));
        }
        out.sort((a, b) -> Double.compare(b.probability(), a.probability()));
        return out.subList(0, Math.min(6, out.size()));
    }

    // ------------------------------------------------------------------

    private List<Disease> pool(String cropCode) {
        if (cropCode == null || cropCode.isBlank() || "auto".equals(cropCode)) return diseases.findAll();
        return diseases.findByCropCodeIn(List.of(cropCode, "general"));
    }

    static double symptomMatch(Disease d, Set<String> selected) {
        Set<String> tags = new HashSet<>(Arrays.asList(d.getSymptomTags().split(",")));
        long hits = selected.stream().filter(tags::contains).count();
        if (hits == 0) return 0;
        double recall = hits / (double) selected.size();     // how many of the farmer's symptoms it explains
        double precision = hits / (double) tags.size();      // how much of the disease picture is present
        return 0.6 * recall + 0.4 * precision;
    }

    private static double[] lesionVector(LeafAnalyzer.Features f) {
        double D = Math.max(1e-9, f.damage());
        return new double[]{f.yellow() / D, f.brown() / D, f.dark() / D, f.white() / D, f.rust() / D};
    }

    static Map<String, Double> patternScores(LeafAnalyzer.Features f, double[] v) {
        Map<String, Double> p = new LinkedHashMap<>();
        double many = ramp(f.spotCount(), 3, 15);
        p.put("SPOTS", Math.max(0.05, many * (1 - 0.8 * ramp(f.largestSpotShare(), 0.55, 0.9))));
        p.put("BLOTCH", Math.max(0.05, ramp(f.largestSpotShare(), 0.3, 0.7) * ramp(f.damage(), 0.03, 0.12)));
        // edge scorch = most damage sits in the edge band; one big blotch that merely touches the edge is not scorch
        p.put("MARGIN", Math.max(0.05, ramp(f.marginShare(), 0.45, 0.8) * (1 - 0.6 * ramp(f.largestSpotShare(), 0.6, 0.95) * (1 - ramp(f.marginShare(), 0.75, 0.95)))));
        p.put("DIFFUSE", Math.max(0.05, ramp(v[0], 0.4, 0.75) * (1 - 0.5 * ramp(f.marginShare(), 0.55, 0.85))));
        p.put("POWDER", Math.max(0.05, ramp(v[3], 0.25, 0.6)));
        p.put("PUSTULES", Math.max(0.05, ramp(v[4], 0.15, 0.45) * Math.max(0.3, ramp(f.spotCount(), 5, 20))));
        p.replaceAll((k, val) -> round(val));
        return p;
    }

    private static List<String> observations(LeafAnalyzer.Features f, Map<String, Double> mix, String pattern) {
        List<String> o = new ArrayList<>();
        o.add(String.format(Locale.ROOT, "%.1f%% of the leaf area shows symptoms", f.damage() * 100));
        if (f.damage() >= HEALTHY_DAMAGE) {
            mix.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e ->
                    o.add(String.format(Locale.ROOT, "Main lesion type: %s (%.0f%% of affected area)", e.getKey(), e.getValue())));
            o.add(f.spotCount() + (f.spotCount() == 1 ? " lesion region" : " separate lesion regions") + " detected");
            if (f.marginShare() > 0.45) o.add(String.format(Locale.ROOT, "%.0f%% of damage is on the leaf edges", f.marginShare() * 100));
            o.add("Spread pattern looks like: " + switch (pattern) {
                case "SPOTS" -> "scattered spots";
                case "BLOTCH" -> "large blotches / blight";
                case "MARGIN" -> "edge scorch from the margins";
                case "DIFFUSE" -> "general yellowing / mosaic";
                case "POWDER" -> "powdery surface growth";
                case "PUSTULES" -> "rust pustules";
                default -> pattern.toLowerCase(Locale.ROOT);
            });
        }
        o.add(String.format(Locale.ROOT, "Leaf fills %.0f%% of the photo", f.leafCoverage() * 100));
        return o;
    }

    static String stage(double D) {
        double p = D * 100;
        if (p < HEALTHY_DAMAGE * 100) return "Healthy";
        if (p < 10) return "Mild";
        if (p < 25) return "Moderate";
        if (p < 50) return "Severe";
        return "Critical";
    }

    private static String urgency(String severity, String stage, boolean uncertain) {
        if (uncertain) return "Confirm with a second photo or the symptom checker, then act";
        if ("Critical".equals(severity) || "Severe".equals(stage) || "Critical".equals(stage)) return "Act within 24 hours";
        if ("High".equals(severity) || "Moderate".equals(stage)) return "Treat within 2-3 days";
        return "Treat this week and monitor";
    }

    private static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0 || nb == 0) return 0;
        return dot / Math.sqrt(na * nb);
    }

    private static double ramp(double x, double lo, double hi) {
        return Math.max(0, Math.min(1, (x - lo) / (hi - lo)));
    }

    private static double pct(double v) { return Math.round(v * 1000) / 10.0; }

    private static double round(double v) { return Math.round(v * 1000) / 1000.0; }
}
