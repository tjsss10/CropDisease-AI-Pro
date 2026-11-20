package com.cropdisease.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * 7-day disease-risk forecast from live weather (Open-Meteo, free, no key).
 *
 * Simple, published-style infection rules computed from hourly temperature,
 * humidity and rain:
 *  - Late blight     : hours with RH >= 90% and 10-24 °C   (Beaumont / Smith-period idea)
 *  - Leaf spot/blight: hours with RH >= 85% and 20-30 °C   (Alternaria, Septoria, Cercospora)
 *  - Rust            : hours with RH >= 95% (dew) and 12-25 °C
 *  - Powdery mildew  : hours 15-28 °C with RH 40-80%, reduced by rain
 *  - Bacterial       : rain >= 5 mm with max temp >= 25 °C (splash + warmth)
 */
@Service
public class WeatherRiskService {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper json;
    private final String forecastUrl;
    private final String geocodeUrl;

    public WeatherRiskService(ObjectMapper json,
                              @Value("${app.weather.forecast-url}") String forecastUrl,
                              @Value("${app.weather.geocode-url}") String geocodeUrl) {
        this.json = json;
        this.forecastUrl = forecastUrl;
        this.geocodeUrl = geocodeUrl;
    }

    public record Place(String name, String region, String country, double latitude, double longitude) { }

    public record DayRisk(String date, double tMax, double tMin, double rain, int humidity, int weatherCode,
                          Map<String, Integer> risks, int overall, String level, String topThreat) { }

    public record Forecast(double latitude, double longitude, Map<String, Object> current,
                           List<DayRisk> days, String summary, List<String> advice) { }

    public List<Place> search(String name) throws Exception {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Type a village or city name.");
        String url = geocodeUrl + "?count=6&language=en&format=json&name=" + URLEncoder.encode(name.trim(), StandardCharsets.UTF_8);
        JsonNode root = get(url);
        List<Place> out = new ArrayList<>();
        JsonNode results = root.path("results");
        for (int i = 0; i < results.size(); i++) {
            JsonNode r = results.get(i);
            out.add(new Place(r.path("name").asString(), r.path("admin1").asString(""), r.path("country").asString(""),
                    r.path("latitude").asDouble(), r.path("longitude").asDouble()));
        }
        return out;
    }

    public Forecast forecast(double lat, double lon) throws Exception {
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new IllegalArgumentException("That location doesn't look right.");
        // Same place (~1 km) within 30 minutes -> reuse; keeps us well under the free API rate limit
        String key = String.format(Locale.ROOT, "%.2f,%.2f", lat, lon);
        Cached hit = cache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.at() < 30 * 60_000L) return hit.forecast();

        String url = String.format(Locale.ROOT, "%s?latitude=%.4f&longitude=%.4f&" + QUERY, forecastUrl, lat, lon);
        JsonNode root;
        try {
            root = get(url);
        } catch (IllegalStateException first) {
            Thread.sleep(1500);            // one retry (rate limits are often momentary)
            root = get(url);
        }
        Forecast f = fromOpenMeteo(root);
        if (cache.size() > 500) cache.clear();
        cache.put(key, new Cached(System.currentTimeMillis(), f));
        return f;
    }

    /** Open-Meteo query parameters (also used by the browser fallback in weather.html / home.html). */
    public static final String QUERY = "timezone=auto&forecast_days=7"
            + "&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m,weather_code"
            + "&hourly=temperature_2m,relative_humidity_2m,precipitation"
            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code,relative_humidity_2m_mean";

    private record Cached(long at, Forecast forecast) { }

    private final Map<String, Cached> cache = new java.util.concurrent.ConcurrentHashMap<>();

    /** Risk model from raw Open-Meteo JSON (fetched by the server, or by the visitor's browser as a fallback). */
    public Forecast fromOpenMeteo(String rawJson) {
        JsonNode root;
        try {
            root = json.readTree(rawJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Weather data could not be read.");
        }
        if (!root.path("daily").path("time").isArray() || !root.path("hourly").path("time").isArray())
            throw new IllegalArgumentException("Weather data is incomplete.");
        return fromOpenMeteo(root);
    }

    private Forecast fromOpenMeteo(JsonNode root) {
        Map<String, Object> current = new LinkedHashMap<>();
        JsonNode c = root.path("current");
        current.put("temperature", c.path("temperature_2m").asDouble());
        current.put("humidity", c.path("relative_humidity_2m").asInt());
        current.put("rain", c.path("precipitation").asDouble());
        current.put("wind", c.path("wind_speed_10m").asDouble());
        current.put("weatherCode", c.path("weather_code").asInt());

        JsonNode hourly = root.path("hourly");
        JsonNode hTime = hourly.path("time"), hT = hourly.path("temperature_2m"), hRH = hourly.path("relative_humidity_2m"), hP = hourly.path("precipitation");
        JsonNode daily = root.path("daily");

        List<DayRisk> days = new ArrayList<>();
        for (int d = 0; d < daily.path("time").size(); d++) {
            String date = daily.path("time").get(d).asString();
            int blightH = 0, spotH = 0, rustH = 0, mildewH = 0;
            double maxRh = 0;
            for (int i = 0; i < hTime.size(); i++) {
                if (!hTime.get(i).asString().startsWith(date)) continue;
                double t = hT.get(i).asDouble(), rh = hRH.get(i).asDouble();
                maxRh = Math.max(maxRh, rh);
                if (rh >= 90 && t >= 10 && t <= 24) blightH++;
                if (rh >= 85 && t >= 20 && t <= 30) spotH++;
                if (rh >= 95 && t >= 12 && t <= 25) rustH++;
                if (t >= 15 && t <= 28 && rh >= 40 && rh <= 80) mildewH++;
            }
            double tMax = daily.path("temperature_2m_max").get(d).asDouble();
            double tMin = daily.path("temperature_2m_min").get(d).asDouble();
            double rain = daily.path("precipitation_sum").get(d).asDouble();
            int rhMean = daily.path("relative_humidity_2m_mean").size() > d ? daily.path("relative_humidity_2m_mean").get(d).asInt() : 0;

            Map<String, Integer> risks = new LinkedHashMap<>();
            risks.put("Late blight", pct(blightH / 12.0));
            risks.put("Leaf spots & early blight", pct(spotH / 10.0));
            risks.put("Rusts", pct(rustH / 8.0));
            // mildew needs mild dry days AND humid nights; heavy rain washes spores off
            double nightFactor = maxRh >= 85 ? 1.0 : maxRh >= 70 ? 0.7 : 0.4;
            risks.put("Powdery mildew", pct(mildewH / 16.0 * nightFactor * (rain >= 2 ? 0.5 : 1.0)));
            risks.put("Bacterial diseases", rain >= 5 && tMax >= 25 ? pct(0.4 + rain / 30.0) : pct(rain / 25.0));

            Map.Entry<String, Integer> top = risks.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
            int overall = top.getValue();
            days.add(new DayRisk(date, tMax, tMin, rain, rhMean, daily.path("weather_code").get(d).asInt(),
                    risks, overall, level(overall), top.getKey()));
        }

        int peak = days.stream().mapToInt(DayRisk::overall).max().orElse(0);
        DayRisk worst = days.stream().max(Comparator.comparingInt(DayRisk::overall)).orElse(null);
        String summary = worst == null ? "No forecast available."
                : level(peak) + " disease pressure this week. Highest risk: " + worst.topThreat() + " on " + worst.date() + ".";

        List<String> advice = new ArrayList<>();
        Map<String, Integer> weekMax = new LinkedHashMap<>();
        for (DayRisk d : days) d.risks().forEach((k, val) -> weekMax.merge(k, val, Math::max));
        if (weekMax.getOrDefault("Late blight", 0) >= 60)
            advice.add("Blight weather ahead: give tomato/potato a protective Mancozeb or copper spray BEFORE the wet spell.");
        if (weekMax.getOrDefault("Leaf spots & early blight", 0) >= 60)
            advice.add("Warm & humid: remove lower infected leaves and keep foliage dry; plan a protectant fungicide spray.");
        if (weekMax.getOrDefault("Rusts", 0) >= 60)
            advice.add("Heavy dew expected: scout wheat, maize, soybean for rust pustules every 2-3 days.");
        if (weekMax.getOrDefault("Powdery mildew", 0) >= 60)
            advice.add("Dry-mild days favour powdery mildew on grape, wheat, peas & cucurbits: sulphur sprays work well preventively.");
        if (weekMax.getOrDefault("Bacterial diseases", 0) >= 60)
            advice.add("Rain + heat: avoid field work on wet plants, sanitise tools and drain standing water.");
        if (days.stream().anyMatch(d -> d.rain() >= 10))
            advice.add("Heavy rain day forecast: don't spray just before it, fungicide will wash off. Spray 24 h before or after.");
        if (advice.isEmpty()) advice.add("Low disease pressure. A good week for routine scouting, weeding and fertiliser work.");

        return new Forecast(root.path("latitude").asDouble(), root.path("longitude").asDouble(), current, days, summary, advice);
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("User-Agent", "CropDiseaseAI/2.0").GET().build();
        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("Weather service is not reachable. Check the internet connection.");
        }
        if (res.statusCode() != 200) throw new IllegalStateException("Weather service returned " + res.statusCode());
        return json.readTree(res.body());
    }

    private static int pct(double v) { return (int) Math.round(Math.max(0, Math.min(1, v)) * 100); }

    static String level(int v) {
        if (v >= 75) return "Very high";
        if (v >= 50) return "High";
        if (v >= 25) return "Moderate";
        return "Low";
    }
}
