package com.cropdisease.controller;

import com.cropdisease.model.Crop;
import com.cropdisease.model.Disease;
import com.cropdisease.repository.CropRepository;
import com.cropdisease.repository.DiseaseRepository;
import com.cropdisease.service.DiagnosisService;
import com.cropdisease.service.KnowledgeBase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Crops, disease encyclopedia and the symptom checker. */
@RestController
@RequestMapping("/api")
public class LibraryController {

    private final CropRepository crops;
    private final DiseaseRepository diseases;
    private final DiagnosisService diagnosis;

    public LibraryController(CropRepository crops, DiseaseRepository diseases, DiagnosisService diagnosis) {
        this.crops = crops;
        this.diseases = diseases;
        this.diagnosis = diagnosis;
    }

    public record CropView(String code, String name, String scientificName, String emoji, String season,
                           String description, long diseaseCount) { }

    @GetMapping("/crops")
    public List<CropView> crops() {
        Map<String, Long> count = new HashMap<>();
        for (Disease d : diseases.findAll()) count.merge(d.getCropCode(), 1L, Long::sum);
        List<CropView> out = new ArrayList<>();
        for (Crop c : crops.findAll()) {
            out.add(new CropView(c.getCode(), c.getName(), c.getScientificName(), c.getEmoji(), c.getSeason(),
                    c.getDescription(), count.getOrDefault(c.getCode(), 0L)));
        }
        out.sort(Comparator.comparing((CropView c) -> "general".equals(c.code())).thenComparing(CropView::name));
        return out;
    }

    @GetMapping("/diseases")
    public List<Disease> diseases(@RequestParam(required = false) String crop,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) String type) {
        List<Disease> list = (crop == null || crop.isBlank()) ? diseases.findAll() : diseases.findByCropCodeOrderByName(crop);
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return list.stream()
                .filter(d -> needle.isEmpty() || (d.getName() + " " + d.getPathogen() + " " + d.getSymptoms() + " " + d.getCropCode())
                        .toLowerCase(Locale.ROOT).contains(needle))
                .filter(d -> type == null || type.isBlank() || d.getType().equalsIgnoreCase(type))
                .sorted(Comparator.comparing(Disease::getCropCode).thenComparing(Disease::getName))
                .toList();
    }

    @GetMapping("/diseases/{code}")
    public ResponseEntity<?> disease(@PathVariable String code) {
        return diseases.findById(code).<ResponseEntity<?>>map(d -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("disease", d);
            out.put("crop", crops.findById(d.getCropCode()).orElse(null));
            out.put("related", diseases.findByCropCodeOrderByName(d.getCropCode()).stream()
                    .filter(x -> !x.getCode().equals(code)).map(x -> Map.of("code", x.getCode(), "name", x.getName(), "type", x.getType())).toList());
            return ResponseEntity.ok(out);
        }).orElse(ResponseEntity.status(404).body(Map.of("message", "Disease not found.")));
    }

    @GetMapping("/symptoms")
    public Map<String, String> symptoms() {
        return KnowledgeBase.SYMPTOMS;
    }

    public record SymptomCheck(String crop, List<String> symptoms) { }

    @PostMapping("/symptom-check")
    public List<DiagnosisService.Candidate> symptomCheck(@RequestBody SymptomCheck r) {
        return diagnosis.bySymptoms(r.crop(), r.symptoms() == null ? Set.of() : new HashSet<>(r.symptoms()));
    }
}
