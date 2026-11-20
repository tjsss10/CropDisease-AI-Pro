package com.cropdisease.service;

import com.cropdisease.model.Crop;
import com.cropdisease.model.Disease;
import com.cropdisease.repository.CropRepository;
import com.cropdisease.repository.DiseaseRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * "Krishi Mitra" farm assistant. Answers questions from the MySQL knowledge base:
 * finds the disease/crop being asked about and the intent (treat, organic, prevent,
 * symptoms, cause, weather), then builds a short answer. No internet needed.
 */
@Service
public class AssistantService {

    private final DiseaseRepository diseases;
    private final CropRepository crops;

    public AssistantService(DiseaseRepository diseases, CropRepository crops) {
        this.diseases = diseases;
        this.crops = crops;
    }

    public record Reply(String text, List<String> bullets, String diseaseCode, List<String> suggestions) { }

    private static final Map<String, List<String>> INTENTS = new LinkedHashMap<>();

    static {
        INTENTS.put("organic", List.of("organic", "natural", "bio", "neem", "home remedy", "desi", "jaivik"));
        INTENTS.put("treat", List.of("treat", "cure", "control", "spray", "medicine", "fungicide", "pesticide", "dawa", "kill", "manage", "chemical"));
        INTENTS.put("prevent", List.of("prevent", "avoid", "stop", "protect", "precaution"));
        INTENTS.put("symptoms", List.of("symptom", "sign", "look like", "identify", "recognise", "recognize", "spot"));
        INTENTS.put("cause", List.of("cause", "why", "reason", "pathogen", "spread", "weather", "condition"));
        INTENTS.put("list", List.of("disease of", "diseases of", "diseases in", "what diseases", "common disease", "list"));
    }

    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("paddy", "rice"), Map.entry("dhan", "rice"), Map.entry("maize", "corn"), Map.entry("makka", "corn"),
            Map.entry("tamatar", "tomato"), Map.entry("aloo", "potato"), Map.entry("gehu", "wheat"), Map.entry("gehun", "wheat"),
            Map.entry("kapas", "cotton"), Map.entry("mirchi", "chilli"), Map.entry("pepper", "chilli"), Map.entry("capsicum", "chilli"),
            Map.entry("ganna", "sugarcane"), Map.entry("angoor", "grape"), Map.entry("seb", "apple"), Map.entry("soya", "soybean"));

    public Reply ask(String question) {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("Type a question.");
        String q = " " + question.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ") + " ";
        for (Map.Entry<String, String> s : SYNONYMS.entrySet()) q = q.replace(" " + s.getKey() + " ", " " + s.getValue() + " ");

        String intent = "overview";
        for (Map.Entry<String, List<String>> e : INTENTS.entrySet()) {
            if (e.getValue().stream().anyMatch(q::contains)) { intent = e.getKey(); break; }
        }

        String crop = null;
        for (Crop c : crops.findAll()) {
            if (q.contains(" " + c.getCode() + " ") || q.contains(" " + c.getName().toLowerCase(Locale.ROOT).split(" ")[0] + " ")) {
                crop = c.getCode();
                break;
            }
        }

        // greetings / help
        if (q.matches(".*\\b(hi|hello|namaste|hey|help)\\b.*") && q.trim().split("\\s+").length <= 3) {
            return new Reply("Namaste! I'm Krishi Mitra, your crop doctor assistant. Ask me about any disease, its symptoms, organic or chemical treatment, or prevention.",
                    List.of(), null, List.of("How to treat tomato early blight?", "Organic control for powdery mildew", "Diseases of rice", "Why does late blight spread?"));
        }

        Disease best = bestDisease(q, crop);

        if ("list".equals(intent) || (best == null && crop != null)) {
            if (crop == null) {
                return new Reply("Which crop? I know tomato, potato, maize, rice, wheat, apple, grape, cotton, chilli, soybean and sugarcane.",
                        List.of(), null, List.of("Diseases of tomato", "Diseases of wheat"));
            }
            final String c = crop;
            String cropName = crops.findById(c).map(Crop::getName).orElse(c);
            List<String> b = new ArrayList<>();
            for (Disease d : diseases.findByCropCodeOrderByName(c)) b.add(d.getName() + " (" + d.getType() + ", " + d.getSeverity() + " risk)");
            return new Reply("Common diseases of " + cropName + ":", b, null,
                    diseases.findByCropCodeOrderByName(c).stream().limit(3).map(d -> "How to treat " + d.getName().toLowerCase(Locale.ROOT) + " in " + c + "?").toList());
        }

        if (best == null) {
            return new Reply("I couldn't match that to a disease in my library. Try naming the crop and the symptom, for example \"yellow spots on tomato leaves\", or use the Scan page to upload a photo.",
                    List.of(), null, List.of("Diseases of potato", "White powder on grape leaves", "Rust on wheat"));
        }

        String name = best.getName() + " of " + crops.findById(best.getCropCode()).map(Crop::getName).orElse("crops");
        Reply r = switch (intent) {
            case "organic" -> new Reply("Organic / natural control of " + name + ":", split(best.getOrganicTreatment()), best.getCode(), List.of());
            case "treat" -> {
                List<String> b = new ArrayList<>(split(best.getChemicalTreatment()));
                b.add("Organic option: " + split(best.getOrganicTreatment()).get(0));
                yield new Reply("Treatment for " + name + " (" + best.getPathogen() + "):", b, best.getCode(), List.of());
            }
            case "prevent" -> new Reply("How to prevent " + name + ":", split(best.getPrevention()), best.getCode(), List.of());
            case "symptoms" -> new Reply("How to recognise " + name + ":", split(best.getSymptoms()), best.getCode(), List.of());
            case "cause" -> new Reply(name + " is caused by " + best.getPathogen() + " (" + best.getType().toLowerCase(Locale.ROOT) + "). It is favoured by: "
                    + best.getFavourableConditions() + ".", split(best.getPrevention()).subList(0, Math.min(2, split(best.getPrevention()).size())), best.getCode(), List.of());
            default -> new Reply(name + " - " + best.getDescription(), split(best.getSymptoms()), best.getCode(), List.of());
        };
        String n = best.getName().toLowerCase(Locale.ROOT);
        return new Reply(r.text(), r.bullets(), r.diseaseCode(),
                List.of("Organic control for " + n, "How to prevent " + n, "Symptoms of " + n, "What causes " + n));
    }

    private Disease bestDisease(String q, String crop) {
        Set<String> words = new HashSet<>(Arrays.asList(q.trim().split("\\s+")));
        Disease best = null;
        double bestScore = 0;
        for (Disease d : diseases.findAll()) {
            double s = 0;
            String name = d.getName().toLowerCase(Locale.ROOT);
            if (q.contains(name.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim())) s += 5;
            for (String w : name.replaceAll("[^a-z0-9 ]", " ").split("\\s+")) {
                if (w.length() > 3 && words.contains(w)) s += 1.5;
            }
            for (String w : d.getSymptoms().toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").split("\\s+")) {
                if (w.length() > 4 && words.contains(w)) s += 0.25;
            }
            if (crop != null) s = d.getCropCode().equals(crop) ? s * 1.6 + 0.5 : ("general".equals(d.getCropCode()) ? s : s * 0.4);
            if (s > bestScore) { bestScore = s; best = d; }
        }
        return bestScore >= 1.4 ? best : null;
    }

    private static List<String> split(String s) {
        return s == null ? List.of() : Arrays.asList(s.split("\\|"));
    }
}
