package com.cropdisease.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spray mix calculator: how much product and water a field needs,
 * how many knapsack tanks, and the cost.
 * Dose rates are typical label rates for foliar sprays in India; always check the label.
 */
@Service
public class SprayCalculator {

    public record Product(String code, String name, String group, double dosePerLitre, String unit,
                          int preHarvestDays, String targets) { }

    public record Result(Product product, double areaAcres, double waterLitres, double productQuantity, String unit,
                         int tanks, double perTank, double tankSize, Double cost, List<String> steps, List<String> safety) { }

    public static final Map<String, Product> PRODUCTS = new LinkedHashMap<>();

    static {
        add("mancozeb", "Mancozeb 75 WP", "Contact fungicide (M03)", 2.5, "g", 7, "Blights, leaf spots, rusts, downy mildew");
        add("copper-oxy", "Copper oxychloride 50 WP", "Contact fungicide/bactericide (M01)", 3.0, "g", 7, "Bacterial spots, blights, downy mildew");
        add("chlorothalonil", "Chlorothalonil 75 WP", "Contact fungicide (M05)", 2.0, "g", 7, "Early blight, leaf spots");
        add("metalaxyl-mz", "Metalaxyl 8% + Mancozeb 64% WP", "Systemic + contact (4 + M03)", 2.5, "g", 7, "Late blight, downy mildew");
        add("azoxystrobin", "Azoxystrobin 23 SC", "Systemic fungicide (11)", 1.0, "ml", 5, "Blights, blast, rusts, sheath blight");
        add("propiconazole", "Propiconazole 25 EC", "Systemic triazole (3)", 1.0, "ml", 15, "Rusts, powdery mildew, leaf spots");
        add("hexaconazole", "Hexaconazole 5 EC", "Systemic triazole (3)", 2.0, "ml", 15, "Powdery mildew, sheath blight, rust");
        add("tebuconazole", "Tebuconazole 25.9 EC", "Systemic triazole (3)", 1.0, "ml", 15, "Rusts, anthracnose");
        add("tricyclazole", "Tricyclazole 75 WP", "Melanin inhibitor (16.1)", 0.6, "g", 15, "Rice blast");
        add("sulphur", "Wettable sulphur 80 WP", "Contact (M02)", 2.5, "g", 3, "Powdery mildew, mites");
        add("imidacloprid", "Imidacloprid 17.8 SL", "Insecticide (4A)", 0.3, "ml", 7, "Whitefly, aphids, jassids (virus vectors)");
        add("neem", "Neem oil 1500 ppm", "Botanical", 5.0, "ml", 0, "Sucking pests, mild fungal suppression");
        add("trichoderma", "Trichoderma viride 1% WP", "Bio-fungicide", 5.0, "g", 0, "Soil-borne diseases, leaf spots");
        add("pseudomonas", "Pseudomonas fluorescens 1% WP", "Bio-agent", 10.0, "g", 0, "Blast, bacterial blight, leaf spots");
    }

    private static void add(String code, String name, String group, double dose, String unit, int phi, String targets) {
        PRODUCTS.put(code, new Product(code, name, group, dose, unit, phi, targets));
    }

    /**
     * @param area       field area
     * @param areaUnit   "acre", "hectare" or "guntha" (40 guntha = 1 acre)
     * @param waterPerAcre spray volume in litres per acre (200 L typical for field crops)
     */
    public Result calculate(String productCode, double area, String areaUnit, Double waterPerAcre,
                            Double tankSize, Double pricePerUnit, Double packSize) {
        Product p = PRODUCTS.get(productCode);
        if (p == null) throw new IllegalArgumentException("Choose a product.");
        if (area <= 0 || area > 10000) throw new IllegalArgumentException("Enter a field area greater than 0.");

        double acres = switch (areaUnit == null ? "acre" : areaUnit) {
            case "hectare" -> area * 2.47105;
            case "guntha" -> area / 40.0;
            default -> area;
        };
        double wpa = waterPerAcre == null || waterPerAcre <= 0 ? 200 : waterPerAcre;
        double tank = tankSize == null || tankSize <= 0 ? 16 : tankSize;

        double water = acres * wpa;
        double product = water * p.dosePerLitre();
        int tanks = (int) Math.ceil(water / tank);
        double perTank = tank * p.dosePerLitre();

        Double cost = null;
        if (pricePerUnit != null && pricePerUnit > 0 && packSize != null && packSize > 0) {
            cost = Math.round(product / packSize * pricePerUnit * 100) / 100.0;
        }

        List<String> steps = new ArrayList<>();
        steps.add(String.format("Fill the %.0f L tank half with clean water.", tank));
        steps.add(String.format("Pre-mix %.1f %s of %s in a bucket with 1 L water (slurry).", perTank, p.unit(), p.name()));
        steps.add("Pour the slurry into the tank, top up with water and shake / stir well.");
        steps.add(String.format("Spray %d tank%s to cover %.2f acres evenly, covering both sides of leaves.", tanks, tanks == 1 ? "" : "s", acres));
        if (p.preHarvestDays() > 0) steps.add("Do not harvest for " + p.preHarvestDays() + " days after spraying (pre-harvest interval).");

        List<String> safety = List.of(
                "Wear gloves, mask, goggles and full sleeves while mixing and spraying.",
                "Spray in the early morning or late evening; never against the wind.",
                "Do not spray if rain is expected within 4-6 hours.",
                "Keep children and animals away; wash hands before eating.",
                "Always read the product label - the label dose is the legal dose.");

        return new Result(p, Math.round(acres * 100) / 100.0, Math.round(water * 10) / 10.0,
                Math.round(product * 10) / 10.0, p.unit(), tanks, Math.round(perTank * 10) / 10.0, tank, cost, steps, safety);
    }
}
