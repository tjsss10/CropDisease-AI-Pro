package com.cropdisease.controller;

import com.cropdisease.model.Media;
import com.cropdisease.model.Scan;
import com.cropdisease.repository.DiseaseRepository;
import com.cropdisease.repository.MediaRepository;
import com.cropdisease.repository.ScanRepository;
import com.cropdisease.repository.UserRepository;
import com.cropdisease.service.DiagnosisService;
import com.cropdisease.service.LeafAnalyzer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AI leaf scan. Guests get the result only; signed-in farmers also get the scan,
 * photo and heatmap saved in MySQL for history, dashboard and reports.
 */
@RestController
@RequestMapping("/api/scans")
public class ScanController {

    private final LeafAnalyzer analyzer;
    private final DiagnosisService diagnosis;
    private final DiseaseRepository diseases;
    private final ScanRepository scans;
    private final MediaRepository media;
    private final UserRepository users;
    private final ObjectMapper json;

    public ScanController(LeafAnalyzer analyzer, DiagnosisService diagnosis, DiseaseRepository diseases,
                          ScanRepository scans, MediaRepository media, UserRepository users, ObjectMapper json) {
        this.analyzer = analyzer;
        this.diagnosis = diagnosis;
        this.diseases = diseases;
        this.scans = scans;
        this.media = media;
        this.users = users;
        this.json = json;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String, Object> analyze(@RequestParam("image") MultipartFile image,
                                       @RequestParam(required = false) String crop,
                                       @RequestParam(required = false) String symptoms,
                                       @RequestParam(required = false) Long userId,
                                       @RequestParam(required = false) String fieldName,
                                       @RequestParam(required = false) String notes) throws Exception {
        if (image == null || image.isEmpty()) throw new IllegalArgumentException("Choose a leaf photo first.");

        long started = System.currentTimeMillis();
        LeafAnalyzer.Result r = analyzer.analyze(image.getBytes());
        Set<String> sym = new HashSet<>();
        if (symptoms != null && !symptoms.isBlank()) for (String s : symptoms.split(",")) if (!s.isBlank()) sym.add(s.trim());

        DiagnosisService.Diagnosis dx = diagnosis.diagnose(r.features(), crop, sym);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("diagnosis", dx);
        out.put("features", r.features());
        out.put("crop", crop == null || crop.isBlank() ? "auto" : crop);
        out.put("symptoms", sym);
        out.put("disease", dx.diseaseCode() == null ? null : diseases.findById(dx.diseaseCode()).orElse(null));
        out.put("analysisMs", System.currentTimeMillis() - started);
        out.put("model", "CropDisease-AI Pro vision engine v2 (HSV segmentation + lesion morphology + signature matching)");

        if (userId != null && users.existsById(userId)) {
            String imgId = UUID.randomUUID().toString().replace("-", "") + ".jpg";
            String heatId = UUID.randomUUID().toString().replace("-", "") + ".png";
            media.save(new Media(imgId, "image/jpeg", r.normalizedJpeg()));
            media.save(new Media(heatId, "image/png", r.heatmapPng()));

            Scan s = new Scan();
            s.setUserId(userId);
            s.setCropCode((String) out.get("crop"));
            s.setDiseaseCode(dx.diseaseCode());
            s.setDiagnosis(dx.title());
            s.setStatus(dx.status());
            s.setConfidence(dx.confidence());
            s.setSeverityPct(dx.severityPct());
            s.setHealthScore(dx.healthScore());
            s.setStage(dx.stage());
            s.setImagePath("/media/" + imgId);
            s.setHeatmapPath("/media/" + heatId);
            s.setFieldName(trim(fieldName, 80));
            s.setNotes(trim(notes, 500));
            out.put("imageUrl", s.getImagePath());
            out.put("heatmapUrl", s.getHeatmapPath());
            s.setResultJson(json.writeValueAsString(out));
            s = scans.save(s);
            out.put("scanId", s.getId());
            out.put("createdAt", s.getCreatedAt());
        } else {
            out.put("imageUrl", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(r.normalizedJpeg()));
            out.put("heatmapUrl", "data:image/png;base64," + Base64.getEncoder().encodeToString(r.heatmapPng()));
            out.put("scanId", null);
        }
        return out;
    }

    @GetMapping
    public List<Scan> history(@RequestParam Long userId) {
        return scans.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, @RequestParam Long userId) throws Exception {
        Scan s = scans.findById(id).filter(x -> userId.equals(x.getUserId())).orElse(null);
        if (s == null) return ResponseEntity.status(404).body(Map.of("message", "Scan not found."));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = json.readValue(s.getResultJson(), LinkedHashMap.class);
        out.put("scanId", s.getId());
        out.put("createdAt", s.getCreatedAt());
        out.put("notes", s.getNotes());
        out.put("fieldName", s.getFieldName());
        return ResponseEntity.ok(out);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, @RequestParam Long userId) {
        Scan s = scans.findById(id).filter(x -> userId.equals(x.getUserId())).orElse(null);
        if (s == null) return ResponseEntity.status(404).body(Map.of("message", "Scan not found."));
        for (String p : new String[]{s.getImagePath(), s.getHeatmapPath()}) {
            if (p != null && p.startsWith("/media/")) media.deleteById(p.substring(7));
        }
        scans.delete(s);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    /** Numbers for the dashboard charts. */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam Long userId) {
        List<Scan> list = scans.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        long healthy = list.stream().filter(s -> "HEALTHY".equals(s.getStatus())).count();
        out.put("total", list.size());
        out.put("healthy", healthy);
        out.put("diseased", list.size() - healthy);
        out.put("avgHealth", list.isEmpty() ? null : Math.round(list.stream().mapToInt(Scan::getHealthScore).average().orElse(0)));
        out.put("avgSeverity", list.isEmpty() ? null : Math.round(list.stream().mapToDouble(Scan::getSeverityPct).average().orElse(0) * 10) / 10.0);

        Map<String, Long> byDisease = new LinkedHashMap<>();
        list.stream().filter(s -> s.getDiseaseCode() != null)
                .forEach(s -> byDisease.merge(s.getDiagnosis().replace("Possible ", ""), 1L, Long::sum));
        out.put("byDisease", sortDesc(byDisease));

        Map<String, Long> byCrop = new LinkedHashMap<>();
        list.forEach(s -> byCrop.merge(s.getCropCode() == null ? "auto" : s.getCropCode(), 1L, Long::sum));
        out.put("byCrop", sortDesc(byCrop));

        // last 30 days: scans per day and average health
        DateTimeFormatter f = DateTimeFormatter.ISO_LOCAL_DATE;
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<Scan> ds = list.stream().filter(s -> s.getCreatedAt().toLocalDate().equals(day)).toList();
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("date", day.format(f));
            p.put("scans", ds.size());
            p.put("health", ds.isEmpty() ? null : Math.round(ds.stream().mapToInt(Scan::getHealthScore).average().orElse(0)));
            trend.add(p);
        }
        out.put("trend", trend);
        out.put("recent", list.stream().limit(5).toList());

        // streak of days with at least one scan (field scouting habit)
        int streak = 0;
        for (int i = 0; i < 365; i++) {
            LocalDate day = today.minusDays(i);
            if (list.stream().anyMatch(s -> s.getCreatedAt().toLocalDate().equals(day))) streak++;
            else if (i > 0) break;
        }
        out.put("scoutStreak", streak);
        return out;
    }

    private static Map<String, Long> sortDesc(Map<String, Long> m) {
        Map<String, Long> out = new LinkedHashMap<>();
        m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
