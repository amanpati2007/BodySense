# BodySense

**AI-powered multi-disease health screening for Android**

BodySense is an Android application that uses machine learning models trained on clinically-validated datasets to provide risk assessments for 7 common diseases. Results are informational — not medical diagnoses.

---

## Features

- **7 Disease Assessments**: Heart Disease, Diabetes, Kidney Disease, Stroke Risk, Parkinson's Disease, Liver Disease, Lung Cancer
- **Real ML Models**: Trained on public, open-licensed datasets; best algorithm selected via cross-validation
- **FastAPI Backend**: Per-disease endpoints with graceful fallback if models are not yet trained
- **Material 3 UI**: Full dark mode, animated loading, disease-specific color themes
- **Offline-friendly**: Heuristic fallback when backend is unavailable

---

## Quick Start

### Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog or later |
| Android SDK | API 24+ (minSdk) |
| Python | 3.9+ |
| JDK | 11 |

---

### 1. Clone the project

```bash
git clone <your-repo-url>
cd bodysense
```

### 2. Set up environment

```bash
# Copy the example env file
cp .env.example .env

# Edit .env and set your API key (if using Firebase/Gemini features)
# GEMINI_API_KEY=your_key_here
```

### 3. Train the ML models (first-time setup)

```bash
# Create a Python virtual environment
python -m venv ML/.venv

# Activate it
# Windows:
ML\.venv\Scripts\activate
# macOS/Linux:
source ML/.venv/bin/activate

# Install ML dependencies
pip install -r ML/requirements.txt

# Train all 7 disease models
python ML/train_all.py
```

> **Note:** Most datasets are downloaded automatically. The Stroke dataset requires manual download from Kaggle (see `ML/train_stroke.py` for instructions).

### 4. Start the Backend

```powershell
# PowerShell (Windows)
.\Backend\start_backend.ps1

# Command Prompt (Windows)
.\Backend\start_backend.bat
```

The script will display:
- **Emulator URL**: `http://10.0.2.2:8000/` (used automatically for Android emulator)
- **Physical Device URL**: Your LAN IP address

### 5. Configure for Physical Device (optional)

Add these lines to `gradle.properties`:

```properties
bodysense.target=device
bodysense.lan.ip=<YOUR_LAN_IP_FROM_STEP_4>
```

### 6. Build and Run

Open the project in Android Studio and run on an emulator or device.

---

## Backend API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Service status |
| `/health` | GET | Per-model load status |
| `/predict/{disease}` | POST | Run prediction |

**Supported disease IDs:** `heart`, `diabetes`, `kidney`, `stroke`, `parkinsons`, `liver`, `lung`

**Example:**
```bash
curl -X POST http://localhost:8000/predict/heart \
  -H "Content-Type: application/json" \
  -d '{"age": 52, "sex": 1, "chest_pain_type": 0, "resting_bp": 125, "cholesterol": 212, "fasting_blood_sugar": 0, "resting_ecg": 1, "max_heart_rate": 168, "exercise_angina": 0, "oldpeak": 1.0, "st_slope": 2}'
```

---

## ML Datasets & Licenses

| Disease | Dataset | Source | License |
|---------|---------|--------|---------|
| Heart | UCI Heart Disease (Cleveland) | [UCI ML Repo](https://archive.ics.uci.edu/dataset/45/heart+disease) | CC BY 4.0 |
| Diabetes | PIMA Indians Diabetes | [UCI ML Repo via Brownlee](https://raw.githubusercontent.com/jbrownlee/Datasets/master/) | Public Domain |
| Kidney | UCI Chronic Kidney Disease | [UCI ML Repo](https://archive.ics.uci.edu/dataset/336/chronic+kidney+disease) | CC BY 4.0 |
| Stroke | Kaggle Stroke Prediction | [Kaggle](https://www.kaggle.com/datasets/fedesoriano/stroke-prediction-dataset) | ODbL |
| Parkinson's | UCI Parkinsons Dataset | [UCI ML Repo](https://archive.ics.uci.edu/dataset/174/parkinsons) | CC BY 4.0 |
| Liver | UCI ILPD | [UCI ML Repo](https://archive.ics.uci.edu/dataset/225/ilpd+indian+liver+patient+dataset) | CC BY 4.0 |
| Lung | Lung Cancer Survey | [Kaggle](https://www.kaggle.com/datasets/mysarahmadbhat/lung-cancer) | CC0 |

---

## Project Structure

```
bodysense/
├── app/                       # Android application
│   └── src/main/java/com/example/
│       ├── MainActivity.kt    # Navigation host
│       ├── MainViewModel.kt   # State management
│       ├── MainRepository.kt  # Data layer
│       ├── NetworkModule.kt   # Retrofit/OkHttp
│       ├── ApiService.kt      # REST interface
│       ├── Models.kt          # Data models + DiseaseConfigs
│       └── ui/
│           ├── DashboardScreen.kt    # Home screen
│           ├── AssessmentScreen.kt   # Disease-aware assessment engine
│           ├── LoadingScreen.kt      # Animated splash
│           ├── LoadingIndicator.kt   # Reusable spinner
│           └── theme/               # BodySense brand theme
├── Backend/                   # FastAPI backend
│   ├── main.py                # API routes + ML model loading
│   ├── schemas.py             # Pydantic input/output models
│   └── requirements.txt
├── ML/                        # Machine learning
│   ├── train_*.py             # Per-disease training scripts
│   ├── train_all.py           # Train all models at once
│   ├── models/                # Saved .pkl files (generated)
│   ├── datasets/              # Downloaded CSVs (generated)
│   └── requirements.txt
└── .env.example               # Environment template
```

---

## Medical Disclaimer

> BodySense is intended for **informational and educational purposes only**. The assessments provided are based on statistical models and do not constitute medical advice, diagnosis, or treatment. Always consult a qualified healthcare professional for medical concerns.

---

## License

Copyright © BodySense. All rights reserved.

Third-party datasets used under their respective open licenses (see table above).
