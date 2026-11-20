package com.cropdisease.service;

import com.cropdisease.model.Crop;
import com.cropdisease.model.Disease;
import com.cropdisease.repository.CropRepository;
import com.cropdisease.repository.DiseaseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Crop & disease knowledge base. Written to MySQL on every start (upsert),
 * so corrections here reach the database without duplicates.
 *
 * Chemical advice names common active ingredients only. Farmers must follow
 * the product label, local regulations and the pre-harvest interval.
 */
@Component
public class KnowledgeBase implements CommandLineRunner {

    /** Symptom vocabulary used by the symptom checker and the scan page. */
    public static final Map<String, String> SYMPTOMS = new LinkedHashMap<>();

    static {
        SYMPTOMS.put("yellowing", "Leaves turning yellow");
        SYMPTOMS.put("brown_spots", "Brown spots on leaves");
        SYMPTOMS.put("dark_spots", "Black / very dark spots");
        SYMPTOMS.put("concentric_rings", "Target-like rings inside spots");
        SYMPTOMS.put("yellow_halo", "Yellow halo around spots");
        SYMPTOMS.put("white_powder", "White powder on leaf surface");
        SYMPTOMS.put("mold_underside", "Fuzzy mould on leaf underside");
        SYMPTOMS.put("orange_pustules", "Orange / rust-coloured pustules");
        SYMPTOMS.put("water_soaked", "Water-soaked, greasy patches");
        SYMPTOMS.put("leaf_curl", "Leaves curling or cupping");
        SYMPTOMS.put("mosaic", "Mosaic / mottled light-dark green");
        SYMPTOMS.put("wilting", "Plant wilting");
        SYMPTOMS.put("stunting", "Stunted growth");
        SYMPTOMS.put("edge_burn", "Scorched / dry leaf edges");
        SYMPTOMS.put("streaks", "Long streaks or stripes");
        SYMPTOMS.put("gray_center", "Spots with grey / white centre");
        SYMPTOMS.put("fruit_rot", "Fruit or grain rotting");
        SYMPTOMS.put("stem_lesion", "Lesions on stem or sheath");
        SYMPTOMS.put("interveinal", "Yellow between green veins");
        SYMPTOMS.put("rapid_spread", "Spreads very fast after rain");
    }

    private final CropRepository crops;
    private final DiseaseRepository diseases;

    public KnowledgeBase(CropRepository crops, DiseaseRepository diseases) {
        this.crops = crops;
        this.diseases = diseases;
    }

    @Override
    public void run(String... args) {
        crops.saveAll(List.of(
                new Crop("tomato", "Tomato", "Solanum lycopersicum", "🍅", "All seasons", "High-value vegetable, very prone to blights, leaf spots and viruses spread by whiteflies."),
                new Crop("potato", "Potato", "Solanum tuberosum", "🥔", "Rabi", "Tuber crop; late blight can destroy a field in a week of cool, wet weather."),
                new Crop("corn", "Maize (Corn)", "Zea mays", "🌽", "Kharif / Rabi", "Cereal grown for grain and fodder; rusts and leaf blights are the main foliar diseases."),
                new Crop("rice", "Rice (Paddy)", "Oryza sativa", "🌾", "Kharif", "Staple cereal; blast and bacterial leaf blight are the most damaging diseases."),
                new Crop("wheat", "Wheat", "Triticum aestivum", "🌾", "Rabi", "Rabi cereal; rusts (yellow, brown, black) and powdery mildew are key threats."),
                new Crop("apple", "Apple", "Malus domestica", "🍎", "Perennial", "Temperate fruit; scab, rusts and rots reduce fruit quality."),
                new Crop("grape", "Grape", "Vitis vinifera", "🍇", "Perennial", "Vine fruit; downy & powdery mildew need regular protection in humid weather."),
                new Crop("cotton", "Cotton", "Gossypium hirsutum", "☁️", "Kharif", "Fibre crop; bacterial blight and leaf curl virus are major problems."),
                new Crop("chilli", "Chilli / Pepper", "Capsicum annuum", "🌶️", "Kharif / Rabi", "Spice and vegetable; leaf curl complex, anthracnose and bacterial spot are common."),
                new Crop("soybean", "Soybean", "Glycine max", "🫘", "Kharif", "Oilseed legume; rust and yellow mosaic virus can cause heavy losses."),
                new Crop("sugarcane", "Sugarcane", "Saccharum officinarum", "🎋", "Perennial", "Long-duration cash crop; red rot is the most feared disease."),
                new Crop("general", "Any crop", "—", "🌱", "—", "Nutrient disorders that can affect any crop.")
        ));

        List<Disease> list = new ArrayList<>();

        // ============================ TOMATO ============================
        list.add(d("tomato-early-blight", "tomato", "Early Blight", "Alternaria solani", "Fungal", "High", "20-40%",
                "A very common fungal disease that starts on older, lower leaves and moves upward, causing heavy leaf drop and sun-scalded fruit.",
                "Brown spots with dark concentric rings (target board look)|Yellow tissue around the spots|Lower leaves affected first, then drop|Dark sunken lesions at the stem end of fruit",
                "Remove and destroy infected lower leaves|Spray Trichoderma viride or Bacillus subtilis bio-fungicide|Neem oil 5 ml/L every 7-10 days|Mulch the soil to stop rain splash",
                "Mancozeb 75 WP @ 2.5 g/L|Chlorothalonil 75 WP @ 2 g/L|Azoxystrobin 23 SC @ 1 ml/L for severe attack|Alternate fungicide groups to avoid resistance",
                "Crop rotation of 2-3 years away from tomato/potato/brinjal|Use certified healthy seed and resistant varieties|Stake plants and keep 60 cm spacing for airflow|Drip irrigation instead of overhead sprinklers",
                "Warm (24-29 °C) humid weather with alternating wet and dry periods",
                "brown_spots,concentric_rings,yellow_halo,yellowing,fruit_rot",
                0.18, 0.55, 0.27, 0.0, 0.0, "SPOTS"));
        list.add(d("tomato-late-blight", "tomato", "Late Blight", "Phytophthora infestans", "Oomycete", "Critical", "50-100%",
                "The most destructive tomato disease. In cool wet weather it can kill a whole field within 7-10 days.",
                "Large irregular greasy, dark-green to black patches|White fuzzy growth on leaf underside in humid mornings|Brown-black lesions on stems|Firm brown rotting patches on fruit",
                "Remove and burn infected plants immediately|Copper-based organic spray (Bordeaux mixture 1%)|Improve drainage and avoid evening irrigation",
                "Metalaxyl 8% + Mancozeb 64% WP @ 2.5 g/L|Cymoxanil 8% + Mancozeb 64% WP @ 3 g/L|Dimethomorph 50 WP @ 1 g/L|Repeat every 7 days during wet weather",
                "Grow resistant hybrids|Never plant near potato fields|Keep foliage dry, use drip irrigation|Spray protectant fungicide before forecast rainy spells",
                "Cool (10-24 °C), very humid (RH > 90%) weather with rain, fog or dew",
                "dark_spots,water_soaked,mold_underside,stem_lesion,fruit_rot,rapid_spread,wilting",
                0.05, 0.40, 0.45, 0.10, 0.0, "BLOTCH"));
        list.add(d("tomato-septoria", "tomato", "Septoria Leaf Spot", "Septoria lycopersici", "Fungal", "Moderate", "10-30%",
                "Produces many small spots on lower leaves causing defoliation, which exposes fruit to sunscald.",
                "Many small round spots (2-3 mm) with grey-white centres|Dark brown borders around spots|Tiny black dots (pycnidia) in spot centres|Leaves yellow and fall off from the bottom up",
                "Pick off spotted leaves early|Bacillus subtilis or copper soap sprays|Avoid working among wet plants",
                "Chlorothalonil 75 WP @ 2 g/L|Mancozeb 75 WP @ 2.5 g/L|Difenoconazole 25 EC @ 0.5 ml/L",
                "Remove crop debris after harvest|Rotate crops for 2 years|Water at the base, not on leaves",
                "Wet, warm weather (20-25 °C) with long periods of leaf wetness",
                "brown_spots,gray_center,yellowing,dark_spots",
                0.15, 0.45, 0.20, 0.20, 0.0, "SPOTS"));
        list.add(d("tomato-bacterial-spot", "tomato", "Bacterial Spot", "Xanthomonas spp.", "Bacterial", "High", "10-50%",
                "Bacterial disease spread by rain splash and infected seed, affecting leaves, stems and fruit.",
                "Small dark, water-soaked spots that turn black|Spots may have a yellow halo|Leaves look ragged as spots drop out|Raised scabby spots on fruit",
                "Use hot-water treated seed (50 °C for 25 min)|Copper hydroxide organic-approved sprays|Remove infected plants",
                "Copper oxychloride 50 WP @ 3 g/L|Copper oxychloride + Streptocycline 100 ppm (where permitted)|Spray at 7-10 day intervals in rainy weather",
                "Use certified disease-free seed|Avoid overhead irrigation|Don't handle plants when wet|Rotate with non-solanaceous crops",
                "Warm (24-30 °C), wet and windy weather",
                "dark_spots,yellow_halo,water_soaked,fruit_rot",
                0.12, 0.30, 0.58, 0.0, 0.0, "SPOTS"));
        list.add(d("tomato-leaf-mold", "tomato", "Leaf Mold", "Passalora fulva", "Fungal", "Moderate", "10-30%",
                "Common in polyhouses and humid fields. Starts as yellow patches on the upper leaf surface.",
                "Pale yellow patches on upper leaf surface|Olive-green to grey velvety mould under the patches|Leaves curl, wither and drop",
                "Increase ventilation and reduce humidity|Remove lower infected leaves|Spray Bacillus subtilis",
                "Chlorothalonil 75 WP @ 2 g/L|Mancozeb 75 WP @ 2.5 g/L|Azoxystrobin 23 SC @ 1 ml/L",
                "Keep RH below 85% in greenhouses|Wider spacing and pruning|Resistant varieties",
                "Relative humidity above 85% with temperatures 21-24 °C",
                "yellowing,mold_underside,leaf_curl",
                0.65, 0.25, 0.0, 0.10, 0.0, "DIFFUSE"));
        list.add(d("tomato-yellow-leaf-curl", "tomato", "Yellow Leaf Curl Virus", "TYLCV (whitefly-transmitted)", "Viral", "Critical", "50-100%",
                "Viral disease spread by whiteflies; infected young plants rarely produce fruit. There is no cure once infected.",
                "Upward curling and cupping of leaves|Yellow leaf margins|Severe stunting, bushy look|Flowers drop, very few fruit",
                "Uproot and destroy infected plants|Yellow sticky traps (10-15 per acre)|Neem oil 5 ml/L to deter whiteflies|Grow barrier crops like maize around the field",
                "Control whitefly: Imidacloprid 17.8 SL @ 0.3 ml/L or Thiamethoxam 25 WG @ 0.3 g/L|Rotate insecticide groups|No chemical cures the virus itself",
                "Use resistant (Ty-gene) hybrids|Raise nursery under 40-mesh insect net|Remove weed hosts|Avoid planting next to infected fields",
                "Hot dry weather with high whitefly populations",
                "leaf_curl,yellowing,stunting,mosaic",
                0.90, 0.10, 0.0, 0.0, 0.0, "DIFFUSE"));
        list.add(d("tomato-mosaic", "tomato", "Tomato Mosaic Virus", "ToMV / TMV", "Viral", "Moderate", "10-25%",
                "Highly contagious virus spread through hands, tools and seed.",
                "Light and dark green mosaic pattern|Fern-like, narrow or distorted leaves|Uneven ripening of fruit",
                "Remove infected plants|Wash hands with soap/milk before handling plants|Disinfect tools",
                "No chemical cure; focus on hygiene|Control sucking pests with recommended insecticides",
                "Resistant varieties|Do not use tobacco near plants|Seed treatment with 10% trisodium phosphate",
                "Spread mechanically at any temperature",
                "mosaic,leaf_curl,stunting,yellowing",
                0.85, 0.10, 0.05, 0.0, 0.0, "DIFFUSE"));

        // ============================ POTATO ============================
        list.add(d("potato-early-blight", "potato", "Early Blight", "Alternaria solani", "Fungal", "High", "20-30%",
                "Causes target-like spots on older leaves; reduces tuber size by destroying leaf area.",
                "Dark brown spots with concentric rings|Yellowing around lesions|Older leaves affected first",
                "Remove infected foliage|Neem oil or Trichoderma sprays|Balanced fertilisation (avoid N stress)",
                "Mancozeb 75 WP @ 2.5 g/L|Chlorothalonil 75 WP @ 2 g/L|Azoxystrobin 23 SC @ 1 ml/L",
                "Rotate crops|Use certified seed tubers|Adequate nitrogen and irrigation",
                "Warm days, dew at night, plant stress",
                "brown_spots,concentric_rings,yellowing,yellow_halo",
                0.20, 0.55, 0.25, 0.0, 0.0, "SPOTS"));
        list.add(d("potato-late-blight", "potato", "Late Blight", "Phytophthora infestans", "Oomycete", "Critical", "40-100%",
                "The disease behind the Irish famine. Spreads explosively in cool, wet weather and rots tubers.",
                "Water-soaked pale green spots turning brown-black|White mould ring on leaf underside|Blackened stems|Reddish-brown dry rot in tubers",
                "Destroy infected haulms|Bordeaux mixture 1%|Earth up rows to protect tubers",
                "Metalaxyl + Mancozeb WP @ 2.5 g/L|Cymoxanil + Mancozeb @ 3 g/L|Dimethomorph 50 WP @ 1 g/L|Spray at 7-day interval in blight weather",
                "Plant certified seed|Resistant varieties (e.g., Kufri Jyoti/Girdhari)|Prophylactic Mancozeb spray when forecast is cool and wet|Dehaulm 10-15 days before harvest",
                "Night temperature > 10 °C, RH > 90% for 11+ hours",
                "dark_spots,water_soaked,mold_underside,stem_lesion,rapid_spread,fruit_rot",
                0.05, 0.40, 0.45, 0.10, 0.0, "BLOTCH"));
        list.add(d("potato-leafroll", "potato", "Potato Leafroll Virus", "PLRV (aphid-transmitted)", "Viral", "High", "30-50%",
                "Aphid-transmitted virus carried in seed tubers.",
                "Upward rolling of lower leaves|Leaves feel stiff and leathery|Pale yellow upper leaves|Stunted plants",
                "Rogue out infected plants|Yellow sticky traps for aphids|Neem oil sprays",
                "Aphid control: Imidacloprid 17.8 SL @ 0.3 ml/L|Thiamethoxam 25 WG @ 0.3 g/L",
                "Use virus-free certified seed|Early planting to avoid aphid peaks",
                "High aphid population",
                "leaf_curl,yellowing,stunting",
                0.85, 0.15, 0.0, 0.0, 0.0, "DIFFUSE"));

        // ============================ MAIZE ============================
        list.add(d("corn-common-rust", "corn", "Common Rust", "Puccinia sorghi", "Fungal", "Moderate", "10-30%",
                "Rust pustules on both leaf surfaces reduce photosynthesis.",
                "Small cinnamon-brown powdery pustules on both leaf surfaces|Pustules turn dark brown-black later|Yellowing of heavily infected leaves",
                "Grow resistant hybrids|Sulphur dust in early stage",
                "Mancozeb 75 WP @ 2.5 g/L|Propiconazole 25 EC @ 1 ml/L|Azoxystrobin + Difenoconazole @ 1 ml/L",
                "Resistant hybrids|Timely sowing|Remove volunteer plants",
                "Cool (16-23 °C) with high humidity and dew",
                "orange_pustules,brown_spots,yellowing",
                0.10, 0.25, 0.0, 0.0, 0.65, "PUSTULES"));
        list.add(d("corn-northern-leaf-blight", "corn", "Northern Leaf Blight", "Exserohilum turcicum", "Fungal", "High", "15-50%",
                "Long cigar-shaped lesions that can blight entire leaves before grain fill.",
                "Long (2.5-15 cm) cigar-shaped grey-green to tan lesions|Lesions start on lower leaves|Dark spores in lesions in humid weather",
                "Crop rotation|Destroy crop residue|Trichoderma seed treatment",
                "Mancozeb 75 WP @ 2.5 g/L|Propiconazole 25 EC @ 1 ml/L|Azoxystrobin 23 SC @ 1 ml/L",
                "Resistant hybrids|Rotate with legumes|Balanced NPK",
                "Moderate temperatures (18-27 °C), heavy dew",
                "brown_spots,gray_center,streaks",
                0.10, 0.60, 0.05, 0.25, 0.0, "BLOTCH"));
        list.add(d("corn-gray-leaf-spot", "corn", "Gray Leaf Spot", "Cercospora zeae-maydis", "Fungal", "High", "10-40%",
                "Rectangular grey lesions bounded by veins; worse under minimum tillage.",
                "Rectangular tan to grey lesions between veins|Lesions merge to blight leaves|Lower leaves first",
                "Residue management and rotation",
                "Azoxystrobin + Propiconazole @ 1 ml/L|Pyraclostrobin 20 WG @ 1 g/L",
                "Resistant hybrids|Tillage to bury residue|Rotation",
                "Warm humid weather with long leaf wetness",
                "gray_center,brown_spots,streaks",
                0.10, 0.45, 0.05, 0.40, 0.0, "SPOTS"));

        // ============================ RICE ============================
        list.add(d("rice-blast", "rice", "Rice Blast", "Magnaporthe oryzae", "Fungal", "Critical", "30-70%",
                "The most serious rice disease, attacking leaves, nodes and panicle neck.",
                "Diamond / spindle-shaped spots with grey centres and brown margins|Neck turns black and panicle breaks (neck blast)|Whole seedlings can burn in nursery",
                "Pseudomonas fluorescens 10 g/L spray|Silicon-rich fertilisers|Avoid excess nitrogen",
                "Tricyclazole 75 WP @ 0.6 g/L|Isoprothiolane 40 EC @ 1.5 ml/L|Azoxystrobin + Tebuconazole @ 1.5 ml/L",
                "Resistant varieties|Split nitrogen doses|Seed treatment with Carbendazim 2 g/kg|Remove collateral weed hosts",
                "Night temperature 20-24 °C, RH > 90%, cloudy weather",
                "gray_center,brown_spots,stem_lesion,rapid_spread",
                0.05, 0.50, 0.10, 0.35, 0.0, "SPOTS"));
        list.add(d("rice-brown-spot", "rice", "Brown Spot", "Bipolaris oryzae", "Fungal", "Moderate", "10-45%",
                "Linked to poor soils and nutrient deficiency; caused the Bengal famine of 1943.",
                "Oval brown spots with grey centre and yellow halo|Spots evenly spread over the leaf|Discoloured grains",
                "Correct potassium & silicon deficiency|Seed treatment with Pseudomonas",
                "Mancozeb 75 WP @ 2.5 g/L|Propiconazole 25 EC @ 1 ml/L|Edifenphos 50 EC @ 1 ml/L",
                "Balanced fertiliser with potassium|Healthy seed|Proper water management",
                "High humidity, 25-30 °C, nutrient-poor soil",
                "brown_spots,yellow_halo,gray_center",
                0.12, 0.68, 0.20, 0.0, 0.0, "SPOTS"));
        list.add(d("rice-bacterial-blight", "rice", "Bacterial Leaf Blight", "Xanthomonas oryzae pv. oryzae", "Bacterial", "Critical", "20-50%",
                "Bacterial disease that dries leaves from the tip and edges; worst after storms and floods.",
                "Yellow to straw-coloured stripes from leaf tip along the margins|Wavy margin between healthy and dry tissue|Milky bacterial ooze in morning|Kresek: seedlings wilt and die",
                "Drain field and avoid flooding|Spray fresh cow-dung extract 20%|Avoid clipping seedling tips",
                "Streptocycline 0.15 g + Copper oxychloride 2.5 g per litre (where permitted)|Repeat after 10-12 days",
                "Resistant varieties (Xa genes)|Avoid excess nitrogen|Clean irrigation channels",
                "Warm (25-34 °C) humid weather, storms, flooding",
                "yellowing,edge_burn,streaks,wilting",
                0.50, 0.30, 0.0, 0.20, 0.0, "MARGIN"));
        list.add(d("rice-sheath-blight", "rice", "Sheath Blight", "Rhizoctonia solani", "Fungal", "High", "20-50%",
                "Soil-borne fungus attacking leaf sheaths near the water line in dense crops.",
                "Oval greenish-grey lesions on sheath near water level|Lesions with brown border (snake-skin look)|Upper leaves dry in severe cases",
                "Trichoderma or Pseudomonas soil application|Wider spacing",
                "Hexaconazole 5 EC @ 2 ml/L|Validamycin 3 L @ 2 ml/L|Azoxystrobin 23 SC @ 1 ml/L",
                "Avoid dense planting|Balanced nitrogen|Remove weeds on bunds",
                "Hot (28-32 °C) humid conditions, dense canopy",
                "stem_lesion,gray_center,brown_spots",
                0.10, 0.50, 0.05, 0.35, 0.0, "BLOTCH"));

        // ============================ WHEAT ============================
        list.add(d("wheat-yellow-rust", "wheat", "Yellow (Stripe) Rust", "Puccinia striiformis", "Fungal", "Critical", "30-70%",
                "Cool-weather rust forming yellow powdery stripes; spreads by wind over long distances.",
                "Bright yellow powdery pustules in stripes along veins|Yellow powder sticks to fingers|Leaves dry out early",
                "Grow resistant varieties|Early sowing",
                "Propiconazole 25 EC @ 1 ml/L|Tebuconazole 25.9 EC @ 1 ml/L|Spray on first appearance, repeat after 15 days",
                "Resistant varieties|Avoid late sowing|Monitor fields in Jan-Feb",
                "Cool (10-15 °C) with dew or light rain",
                "orange_pustules,yellowing,streaks",
                0.45, 0.05, 0.0, 0.0, 0.50, "PUSTULES"));
        list.add(d("wheat-leaf-rust", "wheat", "Leaf (Brown) Rust", "Puccinia triticina", "Fungal", "High", "10-40%",
                "The most widespread wheat rust; scattered orange-brown pustules.",
                "Round orange-brown pustules scattered on upper leaf|Pustules rupture releasing rusty powder|Heavily infected leaves dry",
                "Resistant varieties|Balanced fertilisation",
                "Propiconazole 25 EC @ 1 ml/L|Tebuconazole 25.9 EC @ 1 ml/L|Mancozeb 75 WP @ 2.5 g/L",
                "Resistant varieties|Timely sowing|Destroy volunteer wheat",
                "15-22 °C with leaf wetness",
                "orange_pustules,brown_spots",
                0.10, 0.25, 0.0, 0.0, 0.65, "PUSTULES"));
        list.add(d("wheat-powdery-mildew", "wheat", "Powdery Mildew", "Blumeria graminis f. sp. tritici", "Fungal", "Moderate", "10-30%",
                "White powdery growth on leaves in dense, well-fertilised crops.",
                "White to grey cotton-like powdery patches on leaves|Patches turn dull grey with black dots|Leaves yellow beneath patches",
                "Sulphur 80 WP @ 2.5 g/L|Avoid excess nitrogen|Better spacing",
                "Propiconazole 25 EC @ 1 ml/L|Hexaconazole 5 EC @ 2 ml/L|Wettable sulphur 80 WP @ 2.5 g/L",
                "Resistant varieties|Balanced nitrogen|Avoid very dense sowing",
                "Cool (15-22 °C), humid, cloudy but not rainy",
                "white_powder,yellowing",
                0.15, 0.0, 0.0, 0.85, 0.0, "POWDER"));
        list.add(d("wheat-septoria", "wheat", "Septoria Tritici Blotch", "Zymoseptoria tritici", "Fungal", "Moderate", "10-35%",
                "Blotches with black dots on lower leaves, spread by rain splash.",
                "Irregular tan blotches with tiny black dots|Yellow edges around blotches|Lower leaves die first",
                "Crop rotation|Residue removal",
                "Propiconazole 25 EC @ 1 ml/L|Azoxystrobin 23 SC @ 1 ml/L",
                "Resistant varieties|Rotate crops|Avoid early sowing in wet areas",
                "Cool, wet, windy weather",
                "brown_spots,yellowing,dark_spots",
                0.20, 0.60, 0.20, 0.0, 0.0, "BLOTCH"));

        // ============================ APPLE ============================
        list.add(d("apple-scab", "apple", "Apple Scab", "Venturia inaequalis", "Fungal", "High", "20-70%",
                "Olive-black scabby spots on leaves and fruit; cracked, unmarketable fruit.",
                "Olive-green to black velvety spots on leaves|Leaves twist and fall early|Corky, cracked scabs on fruit",
                "Rake and destroy fallen leaves|Urea 5% spray on fallen leaves in autumn|Lime-sulphur sprays",
                "Captan 50 WP @ 3 g/L|Mancozeb 75 WP @ 3 g/L|Difenoconazole 25 EC @ 0.3 ml/L|Follow the state spray schedule",
                "Resistant varieties|Prune for open canopy|Sanitation of leaf litter",
                "Cool (16-24 °C) wet spring weather",
                "dark_spots,brown_spots,fruit_rot,leaf_curl",
                0.05, 0.30, 0.65, 0.0, 0.0, "SPOTS"));
        list.add(d("apple-black-rot", "apple", "Black Rot / Frogeye Leaf Spot", "Botryosphaeria obtusa", "Fungal", "Moderate", "10-30%",
                "Purple-bordered leaf spots, cankers on branches and black fruit rot.",
                "Small purple spots enlarging to brown 'frog-eye' spots with purple margin|Concentric rings on rotting fruit|Cankers on limbs",
                "Remove mummified fruit and dead wood|Copper spray at bud break",
                "Captan 50 WP @ 3 g/L|Thiophanate-methyl 70 WP @ 1 g/L",
                "Prune out cankers|Orchard sanitation|Avoid tree stress",
                "Warm (20-30 °C) wet weather",
                "brown_spots,concentric_rings,fruit_rot,dark_spots",
                0.10, 0.60, 0.30, 0.0, 0.0, "SPOTS"));
        list.add(d("apple-cedar-rust", "apple", "Cedar Apple Rust", "Gymnosporangium juniperi-virginianae", "Fungal", "Moderate", "5-20%",
                "Needs juniper/cedar as alternate host; bright orange leaf spots.",
                "Bright yellow-orange spots on upper leaf|Tube-like structures under spots|Early leaf fall",
                "Remove nearby juniper hosts where possible|Sulphur sprays",
                "Myclobutanil 10 WP @ 0.4 g/L|Mancozeb 75 WP @ 3 g/L",
                "Resistant varieties|Distance from junipers",
                "Wet spring weather",
                "orange_pustules,yellowing,yellow_halo",
                0.40, 0.05, 0.0, 0.0, 0.55, "SPOTS"));
        list.add(d("apple-powdery-mildew", "apple", "Powdery Mildew", "Podosphaera leucotricha", "Fungal", "Moderate", "10-25%",
                "White powdery coating on new shoots and leaves.",
                "White powdery growth on young leaves and shoots|Leaves narrow, curl and crinkle|Russeting on fruit",
                "Prune infected shoots in winter|Sulphur sprays",
                "Wettable sulphur 80 WP @ 2 g/L|Hexaconazole 5 EC @ 1 ml/L|Myclobutanil @ 0.4 g/L",
                "Prune infected terminals|Resistant varieties",
                "Warm days, cool nights, moderate humidity",
                "white_powder,leaf_curl",
                0.10, 0.05, 0.0, 0.85, 0.0, "POWDER"));

        // ============================ GRAPE ============================
        list.add(d("grape-downy-mildew", "grape", "Downy Mildew", "Plasmopara viticola", "Oomycete", "Critical", "30-80%",
                "Most serious grape disease in rainy periods; destroys leaves and bunches.",
                "Yellow 'oil spots' on upper leaf surface|White downy growth on underside|Spots turn brown|Bunches shrivel",
                "Bordeaux mixture 1%|Remove infected shoots|Improve canopy ventilation",
                "Metalaxyl + Mancozeb @ 2.5 g/L|Dimethomorph 50 WP @ 1 g/L|Fosetyl-Al 80 WP @ 2 g/L",
                "Canopy management|Drip irrigation|Protective sprays before rain",
                "Warm rainy weather, RH > 85%",
                "yellowing,mold_underside,brown_spots,rapid_spread",
                0.50, 0.25, 0.0, 0.25, 0.0, "BLOTCH"));
        list.add(d("grape-powdery-mildew", "grape", "Powdery Mildew", "Erysiphe necator", "Fungal", "High", "20-40%",
                "White ash-like powder on leaves and berries; berries crack.",
                "White powdery patches on both leaf surfaces|Berries crack and split|Grey-white coating on bunches",
                "Sulphur dust or wettable sulphur|Potassium bicarbonate spray|Milk spray (1:9)",
                "Wettable sulphur 80 WP @ 2 g/L|Hexaconazole 5 EC @ 1 ml/L|Tebuconazole @ 1 ml/L",
                "Open canopy|Avoid shade and dense growth",
                "Warm dry days with humid nights",
                "white_powder,fruit_rot",
                0.10, 0.05, 0.0, 0.85, 0.0, "POWDER"));
        list.add(d("grape-black-rot", "grape", "Black Rot", "Guignardia bidwellii", "Fungal", "High", "20-80%",
                "Leaf spots with black dots; berries become hard black mummies.",
                "Tan-brown circular leaf spots with dark border|Black pinpoint dots in spots|Berries shrivel into black mummies",
                "Remove mummies and infected canes|Copper sprays",
                "Mancozeb 75 WP @ 2 g/L|Myclobutanil @ 0.4 g/L|Captan 50 WP @ 2 g/L",
                "Sanitation|Open canopy|Spray from shoot growth to berry set",
                "Warm wet weather",
                "brown_spots,dark_spots,fruit_rot",
                0.05, 0.55, 0.40, 0.0, 0.0, "SPOTS"));
        list.add(d("grape-leaf-blight", "grape", "Leaf Blight (Isariopsis)", "Pseudocercospora vitis", "Fungal", "Moderate", "10-20%",
                "Late-season brown blotches leading to premature defoliation.",
                "Irregular dark brown blotches|Yellow margins|Early leaf fall",
                "Remove infected leaves|Copper spray",
                "Mancozeb 75 WP @ 2 g/L|Carbendazim 50 WP @ 1 g/L",
                "Sanitation and canopy management",
                "Warm humid weather",
                "brown_spots,dark_spots,yellowing",
                0.10, 0.65, 0.25, 0.0, 0.0, "BLOTCH"));
        list.add(d("grape-esca", "grape", "Esca (Black Measles)", "Phaeomoniella / Phaeoacremonium spp.", "Fungal", "High", "10-50%",
                "Wood disease of older vines causing 'tiger-stripe' leaves and sudden collapse.",
                "Yellow/red tiger-stripe areas between veins that turn brown|Small dark spots on berries|Sudden wilting of the vine (apoplexy)",
                "Prune in dry weather and protect cuts with paste|Remove dead wood",
                "No curative chemical; wound protectants after pruning",
                "Avoid large pruning wounds|Remove and burn infected wood",
                "Hot summers after wet springs",
                "interveinal,edge_burn,brown_spots,wilting",
                0.35, 0.50, 0.15, 0.0, 0.0, "MARGIN"));

        // ============================ COTTON ============================
        list.add(d("cotton-bacterial-blight", "cotton", "Bacterial Blight (Angular Leaf Spot)", "Xanthomonas citri pv. malvacearum", "Bacterial", "High", "10-35%",
                "Angular water-soaked spots limited by veins; black arm on stems.",
                "Angular water-soaked spots turning brown-black|Spots bounded by veins|Black lesions on stems (black arm)|Boll rot",
                "Acid-delinted seed|Remove infected debris",
                "Copper oxychloride 50 WP @ 3 g/L + Streptocycline 0.1 g/L (where permitted)",
                "Resistant varieties|Seed treatment|Rotation",
                "Warm humid weather with rain",
                "dark_spots,water_soaked,stem_lesion",
                0.10, 0.40, 0.50, 0.0, 0.0, "SPOTS"));
        list.add(d("cotton-leaf-curl", "cotton", "Cotton Leaf Curl Virus", "CLCuV (whitefly-transmitted)", "Viral", "Critical", "30-80%",
                "Whitefly-spread virus causing curled leaves and enations; severe in north India.",
                "Upward or downward leaf curling|Thickened veins and leaf-like enations underneath|Stunted plants, few bolls",
                "Uproot infected plants early|Yellow sticky traps|Neem oil sprays",
                "Whitefly: Flonicamid 50 WG @ 0.3 g/L or Diafenthiuron 50 WP @ 1.2 g/L",
                "Tolerant varieties|Remove weed hosts|Timely sowing",
                "Hot weather, high whitefly",
                "leaf_curl,stunting,yellowing",
                0.80, 0.15, 0.05, 0.0, 0.0, "DIFFUSE"));
        list.add(d("cotton-alternaria", "cotton", "Alternaria Leaf Spot", "Alternaria macrospora", "Fungal", "Moderate", "10-25%",
                "Brown concentric spots, worse on potassium-deficient crops.",
                "Small brown circular spots with concentric rings|Spots merge, leaves fall",
                "Potassium nutrition|Neem-based sprays",
                "Mancozeb 75 WP @ 2.5 g/L|Propiconazole 25 EC @ 1 ml/L",
                "Balanced NPK|Sanitation",
                "Warm humid weather",
                "brown_spots,concentric_rings,yellow_halo",
                0.15, 0.60, 0.25, 0.0, 0.0, "SPOTS"));

        // ============================ CHILLI ============================
        list.add(d("chilli-leaf-curl", "chilli", "Chilli Leaf Curl", "Begomovirus + thrips/mites complex", "Viral", "Critical", "50-100%",
                "Leaf curl complex caused by whitefly-borne virus, thrips and mites.",
                "Leaves curl upward or downward and become small|Puckered, crinkled leaves|Bushy stunted plants|Flower drop",
                "Yellow & blue sticky traps|Neem oil 5 ml/L|Remove infected plants",
                "Whitefly/thrips: Spinetoram 11.7 SC @ 0.5 ml/L or Fipronil 5 SC @ 1.5 ml/L|Mites: Spiromesifen 22.9 SC @ 0.8 ml/L",
                "Nursery under insect net|Border crop of maize|Tolerant hybrids",
                "Dry warm weather with high vector populations",
                "leaf_curl,stunting,mosaic,yellowing",
                0.75, 0.20, 0.05, 0.0, 0.0, "DIFFUSE"));
        list.add(d("chilli-anthracnose", "chilli", "Anthracnose (Fruit Rot / Die-back)", "Colletotrichum capsici", "Fungal", "High", "20-50%",
                "Sunken spots on ripe fruit and die-back of twigs.",
                "Sunken circular dark spots on fruit with concentric rings|Twigs dry from the tip (die-back)|Black dots in lesions",
                "Remove infected fruit and twigs|Seed treatment with Trichoderma 4 g/kg",
                "Propiconazole 25 EC @ 1 ml/L|Azoxystrobin 23 SC @ 1 ml/L|Mancozeb 75 WP @ 2.5 g/L",
                "Healthy seed|Rotation|Avoid fruit injury",
                "Warm (25-30 °C) humid weather",
                "dark_spots,fruit_rot,concentric_rings,stem_lesion",
                0.05, 0.50, 0.45, 0.0, 0.0, "BLOTCH"));
        list.add(d("chilli-bacterial-spot", "chilli", "Bacterial Leaf Spot", "Xanthomonas euvesicatoria", "Bacterial", "Moderate", "10-30%",
                "Water-soaked spots that turn brown; heavy leaf drop.",
                "Small water-soaked spots turning dark brown|Yellow halo around spots|Leaf drop",
                "Healthy seed|Copper soap sprays",
                "Copper oxychloride 50 WP @ 3 g/L|Copper hydroxide 77 WP @ 2 g/L",
                "Seed treatment|Avoid overhead irrigation",
                "Warm wet weather",
                "dark_spots,yellow_halo,water_soaked",
                0.15, 0.35, 0.50, 0.0, 0.0, "SPOTS"));
        list.add(d("chilli-cercospora", "chilli", "Cercospora Leaf Spot (Frog-eye)", "Cercospora capsici", "Fungal", "Moderate", "10-25%",
                "Frog-eye spots with grey centres on leaves.",
                "Circular spots with light grey centre and dark brown margin|Leaves yellow and fall",
                "Remove infected leaves|Neem oil",
                "Mancozeb 75 WP @ 2.5 g/L|Carbendazim 50 WP @ 1 g/L",
                "Wider spacing|Rotation",
                "Warm humid weather",
                "gray_center,brown_spots,yellowing",
                0.15, 0.50, 0.05, 0.30, 0.0, "SPOTS"));

        // ============================ SOYBEAN ============================
        list.add(d("soybean-rust", "soybean", "Soybean Rust", "Phakopsora pachyrhizi", "Fungal", "Critical", "20-80%",
                "Fast-spreading rust causing premature defoliation.",
                "Small tan to reddish-brown lesions on leaf underside|Raised pustules releasing spores|Rapid yellowing and leaf drop",
                "Early sowing|Resistant varieties",
                "Hexaconazole 5 EC @ 2 ml/L|Propiconazole 25 EC @ 1 ml/L|Tebuconazole 25.9 EC @ 1 ml/L",
                "Resistant varieties|Monitor in humid spells|Timely fungicide at first sign",
                "Long leaf wetness at 15-28 °C",
                "orange_pustules,brown_spots,yellowing,rapid_spread",
                0.15, 0.40, 0.0, 0.0, 0.45, "PUSTULES"));
        list.add(d("soybean-frogeye", "soybean", "Frogeye Leaf Spot", "Cercospora sojina", "Fungal", "Moderate", "10-30%",
                "Circular grey spots with reddish-purple border.",
                "Circular grey-centred spots with reddish-brown margins|Spots merge, leaves drop",
                "Rotation and residue removal",
                "Carbendazim 50 WP @ 1 g/L|Azoxystrobin 23 SC @ 1 ml/L",
                "Resistant varieties|Rotation",
                "Warm humid weather",
                "gray_center,brown_spots",
                0.05, 0.50, 0.15, 0.30, 0.0, "SPOTS"));
        list.add(d("soybean-yellow-mosaic", "soybean", "Yellow Mosaic Virus", "MYMV (whitefly-transmitted)", "Viral", "Critical", "30-80%",
                "Whitefly-spread virus producing bright yellow mosaic.",
                "Bright yellow and green mosaic patches|Leaves become fully yellow|Few, small pods",
                "Uproot infected plants|Yellow sticky traps",
                "Whitefly: Thiamethoxam 25 WG @ 0.3 g/L|Seed treatment with Thiamethoxam 30 FS",
                "Resistant varieties|Timely sowing|Weed management",
                "High whitefly populations",
                "mosaic,yellowing,stunting",
                0.92, 0.08, 0.0, 0.0, 0.0, "DIFFUSE"));

        // ============================ SUGARCANE ============================
        list.add(d("sugarcane-red-rot", "sugarcane", "Red Rot", "Colletotrichum falcatum", "Fungal", "Critical", "30-100%",
                "'Cancer of sugarcane' - internal red rotting with white bands and sour smell.",
                "Yellowing and drying of upper leaves|Red lesions with white centres on leaf midrib|Split cane shows red tissue with white patches|Sour alcoholic smell",
                "Use disease-free setts|Hot-water treat setts (50 °C, 2 h)|Remove and burn clumps",
                "Sett treatment with Carbendazim 50 WP @ 1 g/L for 15 min|Thiophanate-methyl drench",
                "Resistant varieties|Avoid ratooning infected fields|Rotation with rice/green manure",
                "Water-logging, high humidity",
                "yellowing,stem_lesion,gray_center,wilting",
                0.30, 0.50, 0.0, 0.20, 0.0, "BLOTCH"));
        list.add(d("sugarcane-rust", "sugarcane", "Brown Rust", "Puccinia melanocephala", "Fungal", "Moderate", "10-30%",
                "Elongated orange-brown pustules on leaves.",
                "Small elongated yellowish spots becoming orange-brown pustules|Pustules mostly on underside|Leaves dry prematurely",
                "Resistant varieties|Balanced nutrition",
                "Mancozeb 75 WP @ 2.5 g/L|Propiconazole 25 EC @ 1 ml/L",
                "Resistant varieties|Avoid excess nitrogen",
                "Cool humid nights",
                "orange_pustules,streaks,brown_spots",
                0.15, 0.30, 0.0, 0.0, 0.55, "PUSTULES"));

        // ============================ ANY CROP (nutrients) ============================
        list.add(d("general-nitrogen-deficiency", "general", "Nitrogen Deficiency", "Nutrient disorder (N)", "Nutrient", "Moderate", "10-40%",
                "Shortage of nitrogen; the plant moves N from old to new leaves.",
                "Uniform pale-yellow colour of older leaves|Yellowing starts at leaf tip and moves along midrib|Slow, thin growth",
                "Apply compost / vermicompost|Green manure crops|Jeevamrut or fish-amino liquid feed",
                "Urea 2% foliar spray|Top-dress urea as per soil test",
                "Soil testing every season|Split nitrogen doses|Include legumes in rotation",
                "Sandy soil, heavy rain leaching, waterlogging",
                "yellowing,stunting",
                0.95, 0.05, 0.0, 0.0, 0.0, "DIFFUSE"));
        list.add(d("general-potassium-deficiency", "general", "Potassium Deficiency", "Nutrient disorder (K)", "Nutrient", "Moderate", "10-30%",
                "Potassium shortage causing leaf edge scorch on older leaves.",
                "Yellowing then browning of leaf edges (scorch)|Older leaves affected first|Weak stems and lodging",
                "Wood ash or banana-peel compost|Potassium-rich organic manure",
                "Muriate of potash (MOP) as per soil test|Potassium nitrate (13-0-45) 1% foliar spray",
                "Soil testing|Balanced NPK",
                "Sandy or heavily cropped soils",
                "edge_burn,yellowing,brown_spots",
                0.45, 0.55, 0.0, 0.0, 0.0, "MARGIN"));
        list.add(d("general-iron-chlorosis", "general", "Iron / Magnesium Chlorosis", "Nutrient disorder (Fe / Mg)", "Nutrient", "Low", "5-20%",
                "Yellow leaf tissue between green veins. Iron: young leaves; magnesium: older leaves.",
                "Yellow tissue between green veins (interveinal chlorosis)|Iron: newest leaves almost white|Magnesium: older leaves with yellow blotches",
                "Add organic matter to lower soil pH|Seaweed extract sprays",
                "Ferrous sulphate 0.5% + citric acid 0.1% spray|Magnesium sulphate 1% spray|Chelated micronutrient mix",
                "Avoid over-liming|Improve drainage|Soil test for pH",
                "Alkaline / calcareous soils, waterlogging",
                "interveinal,yellowing",
                0.90, 0.05, 0.0, 0.05, 0.0, "DIFFUSE"));

        diseases.saveAll(list);
    }

    private static Disease d(String code, String crop, String name, String pathogen, String type, String severity,
                             String yieldLoss, String description, String symptoms, String organic, String chemical,
                             String prevention, String conditions, String tags,
                             double yellow, double brown, double dark, double white, double rust, String pattern) {
        Disease d = new Disease();
        d.setCode(code);
        d.setCropCode(crop);
        d.setName(name);
        d.setPathogen(pathogen);
        d.setType(type);
        d.setSeverity(severity);
        d.setYieldLoss(yieldLoss);
        d.setDescription(description);
        d.setSymptoms(symptoms);
        d.setOrganicTreatment(organic);
        d.setChemicalTreatment(chemical);
        d.setPrevention(prevention);
        d.setFavourableConditions(conditions);
        d.setSymptomTags(tags);
        d.setSigYellow(yellow);
        d.setSigBrown(brown);
        d.setSigDark(dark);
        d.setSigWhite(white);
        d.setSigRust(rust);
        d.setPattern(pattern);
        return d;
    }
}
