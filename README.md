# CropDisease AI Pro (v2)

AI crop doctor: photograph a leaf and get the disease, a confidence score, how much of the leaf is affected,
a colour heatmap of the lesions, and an organic + chemical treatment plan with a 14-day schedule.

Built the same way as FitJanta: **Spring Boot (Java) + MySQL + plain HTML / CSS / JS (Bootstrap 5)**,
Gradle wrapper, one-click start script and a QR page for phones.

## Permanent link

**https://cropdisease-ai-pro.onrender.com** (Render free web service, built from this repo's `Dockerfile`, Singapore).
Every `git push` to `main` redeploys automatically. Free instances sleep after ~15 min idle; the first visit then takes ~30-60 s.

Storage in the cloud: without database settings the app uses a built-in in-memory database (disease library always
loaded; accounts & scans reset when the instance restarts). To keep data permanently, add `DB_URL`, `DB_USER`,
`DB_PASSWORD` (e.g. TiDB Cloud MySQL, `jdbc:mysql://HOST:4000/cropdisease?sslMode=REQUIRED&createDatabaseIfNotExist=true`)
under Render > cropdisease-ai-pro > Environment.

## Run it locally

1. Make sure MySQL (service `MYSQL80`) is running.
2. Double-click **`start-cropdisease.bat`**. It waits for MySQL, starts the server and opens the QR page.
   Or in VS Code: open this folder, then run `.\gradlew.bat bootRun` in the terminal
   (or press F5 using the "CropDisease AI Pro" launch config).
3. Open **http://localhost:8081**. On a phone (same Wi-Fi), scan the QR code at `/qr.html`.

Port 8081 is used so it can run alongside FitJanta (8080).
The MySQL login is in `config/application.properties` (local only, git-ignored). The database `cropdisease`
and all tables are created automatically, and the disease knowledge base is loaded on every start.

## Features

| Page | What it does |
|---|---|
| AI Leaf Scan (`scan.html`) | Camera / upload / drag-drop / paste, crop picker, optional symptoms, animated analysis, result with confidence ring, health score, % affected, stage, urgency, photo-vs-heatmap slider, lesion breakdown, differential diagnosis, treatment tabs, 14-day plan, voice read-out |
| Dashboard (`home.html`) | Live local weather risk, scan totals, 30-day health trend, disease breakdown chart, scouting streak, recent scans |
| History + Report | All saved scans with search & filters, full re-open, delete, printable **PDF report** |
| Disease Library | 45 diseases & disorders across 11 crops: symptoms, cause, organic / chemical control, prevention, AI signature |
| Symptom Checker | No photo needed: tick symptoms, get ranked likely diseases |
| Weather Risk | 7-day infection risk (late blight, leaf spots, rusts, powdery mildew, bacterial) from live Open-Meteo weather, village search or GPS |
| Spray Calculator | Product dose, water, number of tanks, per-tank mix and cost for acres / hectares / guntha |
| Krishi Mitra | Chat assistant over the knowledge base, with voice input and read-out |
| Accounts | Sign up / login (BCrypt), farm profile. **Guest mode** scans without storing anything |

## How the AI works (honest version)

The original CropDisease-AI-V2 zip contained only empty Python files (no code, no trained model, no data), so this
version uses a **pure-Java computer-vision engine** instead of a trained neural network:

1. **Leaf segmentation**: HSV colour model, morphological closing, largest-region selection, hole filling.
2. **Pixel classification** into healthy green, chlorosis (yellow), necrosis (brown), dark lesions,
   powdery/mildew (white-grey) and rust (orange).
3. **Lesion measurement**: % leaf area affected, number and size of spots, damage on the leaf margin, sharpness and brightness checks.
4. **Signature matching**: colour-mix cosine similarity + spread pattern (spots, blotch, margin, diffuse, powder, pustules),
   optionally fused with the farmer's ticked symptoms, then softmax probabilities adjusted for photo quality.

It works best with **one leaf on a plain background in daylight**. Diseases that look alike (e.g. early blight vs Septoria)
get lower confidence and are flagged "needs confirmation". It is an assistant, not a lab test. A trained CNN
(e.g. on PlantVillage) could later replace step 4 behind the same `/api/scans/analyze` endpoint.

## API

| Method | Path | |
|---|---|---|
| POST | `/api/scans/analyze` | multipart `image`, `crop`, `symptoms`, `userId?`, `fieldName?`, `notes?` |
| GET | `/api/scans?userId=` · `/api/scans/{id}?userId=` · `/api/scans/stats?userId=` | history, one scan, dashboard |
| DELETE | `/api/scans/{id}?userId=` | delete a scan + its photos |
| GET | `/api/crops` · `/api/diseases?crop=&type=&q=` · `/api/diseases/{code}` · `/api/symptoms` | knowledge base |
| POST | `/api/symptom-check` | `{crop, symptoms[]}` |
| GET | `/api/weather/risk?lat=&lon=` · `/api/weather/search?q=` | disease-risk forecast |
| GET/POST | `/api/spray/products` · `/api/spray/calculate` | spray calculator |
| POST | `/api/assistant` | `{question}` |
| POST | `/api/users/signup` · `/api/users/login` · PUT `/api/users/{id}` | accounts |

## Project layout

```
src/main/java/com/cropdisease/
  controller/   ScanController, LibraryController, ToolsController, UserController, MediaController, ServerInfoController
  service/      LeafAnalyzer (vision), DiagnosisService, KnowledgeBase (seed data), WeatherRiskService, SprayCalculator, AssistantService
  model/        User, Crop, Disease, Scan, Media (JPA -> MySQL)
src/main/resources/static/   HTML pages, css/app.css, js/app.js
src/test/java/.../LeafAnalyzerTest.java   vision engine tests (./gradlew.bat test)
mysql-queries.sql            handy queries for MySQL Workbench
```

Chemical advice lists common active ingredients only. Always follow the product label, local rules and pre-harvest intervals.
