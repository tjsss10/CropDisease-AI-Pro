package com.cropdisease.controller;

import com.cropdisease.service.AssistantService;
import com.cropdisease.service.SprayCalculator;
import com.cropdisease.service.WeatherRiskService;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Weather risk forecast, spray calculator and the Krishi Mitra assistant. */
@RestController
@RequestMapping("/api")
public class ToolsController {

    private final WeatherRiskService weather;
    private final SprayCalculator spray;
    private final AssistantService assistant;

    public ToolsController(WeatherRiskService weather, SprayCalculator spray, AssistantService assistant) {
        this.weather = weather;
        this.spray = spray;
        this.assistant = assistant;
    }

    @GetMapping("/weather/search")
    public List<WeatherRiskService.Place> search(@RequestParam String q) throws Exception {
        return weather.search(q);
    }

    @GetMapping("/weather/risk")
    public WeatherRiskService.Forecast risk(@RequestParam double lat, @RequestParam double lon) throws Exception {
        return weather.forecast(lat, lon);
    }

    /** Fallback: the browser fetched Open-Meteo itself (cloud server rate-limited); compute the risk here. */
    @PostMapping(value = "/weather/risk", consumes = "application/json")
    public WeatherRiskService.Forecast riskFromData(@RequestBody String openMeteoJson) {
        if (openMeteoJson.length() > 400_000) throw new IllegalArgumentException("Weather data is too large.");
        return weather.fromOpenMeteo(openMeteoJson);
    }

    @GetMapping("/weather/query")
    public Map<String, String> weatherQuery() {
        return Map.of("url", "https://api.open-meteo.com/v1/forecast", "query", WeatherRiskService.QUERY);
    }

    @GetMapping("/spray/products")
    public Collection<SprayCalculator.Product> products() {
        return SprayCalculator.PRODUCTS.values();
    }

    public record SprayRequest(String product, double area, String areaUnit, Double waterPerAcre,
                               Double tankSize, Double price, Double packSize) { }

    @PostMapping("/spray/calculate")
    public SprayCalculator.Result calculate(@RequestBody SprayRequest r) {
        return spray.calculate(r.product(), r.area(), r.areaUnit(), r.waterPerAcre(), r.tankSize(), r.price(), r.packSize());
    }

    public record Question(String question) { }

    @PostMapping("/assistant")
    public AssistantService.Reply ask(@RequestBody Question q) {
        return assistant.ask(q.question());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "app", "CropDisease AI Pro", "version", "2.0.0");
    }
}
