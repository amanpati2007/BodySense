"""
BodySense FastAPI Backend
=========================
Production-ready multi-disease health prediction API.

Endpoints:
  GET  /           - Root status
  GET  /health     - Detailed health check (all models)
  POST /predict/{disease} - Run prediction for a disease

Supported diseases: heart, diabetes, kidney, stroke, parkinsons, liver, lung
"""

import logging
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

import joblib
import pandas as pd
import numpy as np
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, Response

from schemas import (
    DiabetesInput,
    HeartInput,
    KidneyInput,
    LiverInput,
    LungInput,
    ParkinsonInput,
    PredictionResponse,
    ReportRequest,
    StrokeInput,
)

import io
from reportlab.lib.pagesizes import letter
from reportlab.pdfgen import canvas
from reportlab.lib import colors
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle
from reportlab.lib.styles import getSampleStyleSheet

# ─── Logging ──────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger("bodysense")

# ─── Paths ────────────────────────────────────────────────────────────────────
MODELS_DIR = Path(__file__).parent.parent / "ML" / "models"

# ─── Model Registry ───────────────────────────────────────────────────────────
DISEASE_MODELS: dict[str, dict[str, Any]] = {
    "heart":      {"model": None, "scaler": None, "loaded": False},
    "diabetes":   {"model": None, "scaler": None, "loaded": False},
    "kidney":     {"model": None, "scaler": None, "loaded": False},
    "stroke":     {"model": None, "scaler": None, "loaded": False},
    "parkinsons": {"model": None, "scaler": None, "loaded": False},
    "liver":      {"model": None, "scaler": None, "loaded": False},
    "lung":       {"model": None, "scaler": None, "loaded": False},
}


def _load_model(disease: str) -> None:
    """Load model and scaler for a disease. Logs a warning if files are missing."""
    model_path = MODELS_DIR / f"{disease}_model.pkl"
    scaler_path = MODELS_DIR / f"{disease}_scaler.pkl"

    if model_path.exists():
        DISEASE_MODELS[disease]["model"] = joblib.load(model_path)
        log.info("Loaded model: %s", model_path.name)
    else:
        log.warning("Model file not found (will use heuristic): %s", model_path)

    if scaler_path.exists():
        DISEASE_MODELS[disease]["scaler"] = joblib.load(scaler_path)
        log.info("Loaded scaler: %s", scaler_path.name)
    else:
        log.warning("Scaler file not found: %s", scaler_path)

    DISEASE_MODELS[disease]["loaded"] = model_path.exists()


# ─── Startup Tracking ────────────────────────────────────────────────────────
_startup_time: float = 0.0
_predictions_served: int = 0

# ─── Lifespan ─────────────────────────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    global _startup_time
    log.info("BodySense backend starting — loading ML models...")
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    for disease in DISEASE_MODELS:
        _load_model(disease)
    loaded = [d for d, v in DISEASE_MODELS.items() if v["loaded"]]
    log.info("Models loaded: %s / %d", loaded, len(DISEASE_MODELS))
    _startup_time = time.time()
    log.info("BodySense backend ready.")
    yield
    log.info("BodySense backend shutting down.")


# ─── App ──────────────────────────────────────────────────────────────────────
app = FastAPI(
    title="BodySense API",
    description="Multi-disease health prediction API for the BodySense Android app.",
    version="1.9.0",
    lifespan=lifespan,
)

# CORS: Restrict to localhost and internal app origins for production hardening.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost", "https://localhost", "app://bodysense"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
    allow_credentials=False,
)

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    log.error(f"Unhandled Exception on {request.method} {request.url}: {exc}")
    return JSONResponse(
        status_code=500,
        content={"detail": "An internal server error occurred.", "type": type(exc).__name__}
    )

@app.exception_handler(ValueError)
async def value_error_handler(request: Request, exc: ValueError):
    log.warning(f"Value Error on {request.method} {request.url}: {exc}")
    return JSONResponse(
        status_code=400,
        content={"detail": str(exc), "type": "ValueError"}
    )


# ─── Request Timing + Prediction Counter Middleware ───────────────────────────
@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    global _predictions_served
    start = time.perf_counter()
    response = await call_next(request)
    duration_ms = (time.perf_counter() - start) * 1000
    response.headers["X-Process-Time-Ms"] = f"{duration_ms:.2f}"
    # Count successful predictions
    if request.url.path.startswith("/predict/") and response.status_code == 200:
        _predictions_served += 1
    return response


# ─── Routes ───────────────────────────────────────────────────────────────────
@app.get("/", tags=["Status"])
def root():
    return {"status": "running", "service": "BodySense API", "version": "1.9.0"}


@app.get("/health", tags=["Status"])
def health():
    """Returns load status for all disease models plus server uptime and prediction count."""
    global _startup_time, _predictions_served
    model_status = {
        disease: ("loaded" if info["loaded"] else "heuristic_only")
        for disease, info in DISEASE_MODELS.items()
    }
    all_loaded = all(v["loaded"] for v in DISEASE_MODELS.values())
    uptime = round(time.time() - _startup_time, 1) if _startup_time > 0 else 0
    return {
        "status": "healthy" if all_loaded else "partial",
        "models": model_status,
        "uptime_seconds": uptime,
        "predictions_served": _predictions_served,
        "version": "1.9.0",
    }


# ─── Prediction Helper ────────────────────────────────────────────────────────
import pandas as pd

def _run_prediction(disease: str, features_dict: dict) -> tuple[float, float, str, list]:
    """
    Run model prediction or fall back to a simple heuristic.

    Returns:
        (risk_score 0-100, confidence 0-1, method)
    """
    entry = DISEASE_MODELS[disease]
    model = entry["model"]
    scaler = entry["scaler"]

    if model is not None:
        df = pd.DataFrame([features_dict])
        scaled_arr = None
        if scaler is not None:
            try:
                scaled_arr = scaler.transform(df)
            except Exception as e:
                log.error("Scaler transform failed for %s: %s", disease, e)
        else:
            scaled_arr = df.values

        try:
            proba = model.predict_proba(scaled_arr if scaler is not None else df)[0]
            risk_score = float(proba[1]) * 100.0
            confidence = float(max(proba))
            
            # Extract Explainability (SHAP/Coefficients)
            contributions = []
            try:
                feature_names = scaler.get_feature_names_out() if hasattr(scaler, 'get_feature_names_out') else list(features_dict.keys())
                
                if hasattr(model, 'coef_'):
                    weights = model.coef_[0]
                    for i, name in enumerate(feature_names):
                        contributions.append({"feature": name, "contribution": float(weights[i] * scaled_arr[0, i]), "description": ""})
                elif hasattr(model, 'feature_importances_'):
                    import shap
                    explainer = shap.TreeExplainer(model)
                    shap_vals = explainer.shap_values(scaled_arr)
                    shap_arr = np.array(shap_vals)
                    if len(shap_arr.shape) == 3:
                        shap_contribs = shap_arr[0, :, 1]
                    else:
                        shap_contribs = shap_arr[0, :]
                    for i, name in enumerate(feature_names):
                        contributions.append({"feature": name, "contribution": float(shap_contribs[i]), "description": ""})
                        
                # Sort by absolute contribution (highest impact first)
                contributions.sort(key=lambda x: abs(x["contribution"]), reverse=True)
                
                # Keep top 5
                contributions = contributions[:5]
                
                # Generate explanation
                explanation = "Your risk assessment was based on these primary factors. "
                if contributions:
                    pos_factors = [c['feature'].replace('_', ' ').replace('num  ', '').replace('cat  ', '') for c in contributions if c['contribution'] > 0]
                    neg_factors = [c['feature'].replace('_', ' ').replace('num  ', '').replace('cat  ', '') for c in contributions if c['contribution'] < 0]
                    if pos_factors:
                        explanation += f"Factors like {', '.join(pos_factors)} increased your predicted risk. "
                    if neg_factors:
                        explanation += f"Meanwhile, factors like {', '.join(neg_factors)} helped lower your predicted risk."
                
            except Exception as e:
                log.error("SHAP extraction failed for %s: %s", disease, e)
                explanation = ""

            return risk_score, confidence, "ml_model", contributions, explanation

        except Exception as e:
            log.error("Model prediction failed for %s: %s", disease, e)

    # Fallback heuristic (average of numeric values)
    numeric_vals = [float(v) for v in features_dict.values() if isinstance(v, (int, float))]
    if numeric_vals:
        heuristic = min(100.0, max(0.0, float(np.mean(np.abs(numeric_vals))) * 0.5))
    else:
        heuristic = 50.0
    return heuristic, 0.5, "heuristic", [], ""


# ─── Disease Prediction Endpoints ─────────────────────────────────────────────
@app.post("/predict/heart", response_model=PredictionResponse, tags=["Prediction"])
def predict_heart(data: HeartInput):
    features = {
        "age": data.age, "sex": data.sex, "cp": data.chest_pain_type,
        "trestbps": data.resting_bp, "chol": data.cholesterol,
        "fbs": data.fasting_blood_sugar, "restecg": data.resting_ecg,
        "thalach": data.max_heart_rate, "exang": data.exercise_angina,
        "oldpeak": data.oldpeak, "slope": data.st_slope,
    }
    risk, confidence, method, contribs, explanation = _run_prediction("heart", features)
    return PredictionResponse(disease="heart", risk=risk, confidence=confidence, method=method, top_contributors=contribs, explanation=explanation)


@app.post("/predict/diabetes", response_model=PredictionResponse, tags=["Prediction"])
def predict_diabetes(data: DiabetesInput):
    features = {
        "pregnancies": data.pregnancies, "glucose": data.glucose,
        "blood_pressure": data.blood_pressure, "skin_thickness": data.skin_thickness,
        "insulin": data.insulin, "bmi": data.bmi,
        "diabetes_pedigree": data.diabetes_pedigree, "age": data.age,
    }
    risk, confidence, method, contribs, explanation = _run_prediction("diabetes", features)
    return PredictionResponse(disease="diabetes", risk=risk, confidence=confidence, method=method, top_contributors=contribs, explanation=explanation)


@app.post("/predict/kidney", response_model=PredictionResponse, tags=["Prediction"])
def predict_kidney(data: KidneyInput):
    features = {
        "age": data.age, "bp": data.blood_pressure, "sg": data.specific_gravity,
        "al": data.albumin, "su": data.sugar, "bgr": data.blood_glucose_random,
        "bu": data.blood_urea, "sc": data.serum_creatinine, "sod": data.sodium,
        "pot": data.potassium, "hemo": data.haemoglobin, "pcv": data.packed_cell_volume,
        "wbcc": data.wbc_count, "rbcc": data.rbc_count,
        "rbc": data.red_blood_cells, "pc": data.pus_cell, "pcc": data.pus_cell_clumps,
        "ba": data.bacteria, "htn": data.hypertension, "dm": data.diabetes_mellitus,
        "cad": data.coronary_artery_disease, "appet": data.appetite,
        "pe": data.pedal_edema, "ane": data.anemia,
    }
    risk, confidence, method, contribs, explanation = _run_prediction("kidney", features)
    return PredictionResponse(disease="kidney", risk=risk, confidence=confidence, method=method, top_contributors=contribs, explanation=explanation)


@app.post("/predict/stroke", response_model=PredictionResponse, tags=["Prediction"])
def predict_stroke(data: StrokeInput):
    features = {
        "age": data.age, "hypertension": data.hypertension, "heart_disease": data.heart_disease,
        "ever_married": data.ever_married, "work_type": data.work_type,
        "Residence_type": data.residence_type, "avg_glucose_level": data.avg_glucose_level,
        "bmi": data.bmi, "smoking_status": data.smoking_status,
    }
    risk, confidence, method, contribs, explanation = _run_prediction("stroke", features)
    return PredictionResponse(disease="stroke", risk=risk, confidence=confidence, method=method, top_contributors=contribs, explanation=explanation)


@app.post("/predict/parkinsons", response_model=PredictionResponse, tags=["Prediction"])
def predict_parkinsons(data: ParkinsonInput):
    features = {
        "mdvp_fo": data.mdvp_fo, "mdvp_fhi": data.mdvp_fhi, "mdvp_flo": data.mdvp_flo,
        "mdvp_jitter_percent": data.mdvp_jitter_percent, "mdvp_jitter_abs": data.mdvp_jitter_abs,
        "mdvp_rap": data.mdvp_rap, "mdvp_ppq": data.mdvp_ppq, "jitter_ddp": data.jitter_ddp,
        "mdvp_shimmer": data.mdvp_shimmer, "mdvp_shimmer_db": data.mdvp_shimmer_db,
        "shimmer_apq3": data.shimmer_apq3, "shimmer_apq5": data.shimmer_apq5,
        "mdvp_apq": data.mdvp_apq, "shimmer_dda": data.shimmer_dda,
        "nhr": data.nhr, "hnr": data.hnr, "rpde": data.rpde, "dfa": data.dfa,
        "spread1": data.spread1, "spread2": data.spread2, "d2": data.d2, "ppe": data.ppe,
    }
    risk, confidence, method, contribs, explanation = _run_prediction("parkinsons", features)
    return PredictionResponse(disease="parkinsons", risk=risk, confidence=confidence, method=method, top_contributors=contribs, explanation=explanation)


@app.post("/predict/liver", response_model=PredictionResponse, tags=["Prediction"])
def predict_liver(data: LiverInput):
    features = {
        "age": data.age, "gender": data.gender, "total_bilirubin": data.total_bilirubin,
        "direct_bilirubin": data.direct_bilirubin, "alkaline_phosphotase": data.alkaline_phosphotase,
        "alamine_aminotransferase": data.alamine_aminotransferase,
        "aspartate_aminotransferase": data.aspartate_aminotransferase,
        "total_proteins": data.total_proteins, "albumin": data.albumin,
        "albumin_globulin_ratio": data.albumin_globulin_ratio,
    }
    risk, confidence, method, contribs, explanation = _run_prediction("liver", features)
    return PredictionResponse(disease="liver", risk=risk, confidence=confidence, method=method, top_contributors=contribs, explanation=explanation)


@app.post("/predict/lung", response_model=PredictionResponse, tags=["Prediction"])
def predict_lung(data: LungInput):
    features = {
        "GENDER": data.gender, "AGE": data.age, "SMOKING": data.smoking,
        "YELLOW_FINGERS": data.yellow_fingers, "ANXIETY": data.anxiety,
        "PEER_PRESSURE": data.peer_pressure, "CHRONIC_DISEASE": data.chronic_disease,
        "FATIGUE": data.fatigue, "ALLERGY": data.allergy, "WHEEZING": data.wheezing,
        "ALCOHOL_CONSUMING": data.alcohol_consuming, "COUGHING": data.coughing,
        "SHORTNESS_OF_BREATH": data.shortness_of_breath,
        "SWALLOWING_DIFFICULTY": data.swallowing_difficulty, "CHEST_PAIN": data.chest_pain,
    }
    risk, confidence, method, contribs, explanation = _run_prediction("lung", features)
    return PredictionResponse(disease="lung", risk=risk, confidence=confidence, method=method, top_contributors=contribs, explanation=explanation)


# ─── Legacy endpoint (backward compat with original Android client) ────────────
@app.post("/predict", tags=["Legacy"])
def predict_legacy(data: HeartInput):
    """Kept for backward compatibility. Use /predict/heart instead."""
    features = {
        "age": data.age, "sex": data.sex, "cp": data.chest_pain_type,
        "trestbps": data.resting_bp, "chol": data.cholesterol,
        "fbs": data.fasting_blood_sugar, "restecg": data.resting_ecg,
        "thalach": data.max_heart_rate, "exang": data.exercise_angina,
        "oldpeak": data.oldpeak, "slope": data.st_slope,
    }
    risk, confidence, method, contribs, explanation = _run_prediction("heart", features)
    # Return original format for backward compat
    return {"risk": risk}


# ─── PDF Report Endpoint ──────────────────────────────────────────────────────
@app.post("/report", tags=["Reporting"])
def generate_report(data: ReportRequest):
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=letter, rightMargin=30, leftMargin=30, topMargin=30, bottomMargin=18)
    styles = getSampleStyleSheet()
    elements = []

    # Title
    elements.append(Paragraph("BodySense Health Report", styles['Title']))
    elements.append(Spacer(1, 20))

    # Disease Name
    elements.append(Paragraph(f"Assessment: {data.disease_name}", styles['Heading2']))
    
    # Risk Result
    risk_text = f"<b>Risk Level:</b> {data.risk:.1f}%"
    elements.append(Paragraph(risk_text, styles['Normal']))
    
    conf_text = f"<b>AI Confidence:</b> {data.confidence * 100:.1f}%"
    elements.append(Paragraph(conf_text, styles['Normal']))
    elements.append(Spacer(1, 20))

    # Table of Inputs
    elements.append(Paragraph("Patient Inputs:", styles['Heading3']))
    
    table_data = [["Field", "Value"]]
    for k, v in data.patient_inputs.items():
        table_data.append([k, str(v)])
        
    t = Table(table_data, colWidths=[200, 200])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.grey),
        ('TEXTCOLOR', (0,0), (-1,0), colors.whitesmoke),
        ('ALIGN', (0,0), (-1,-1), 'LEFT'),
        ('FONTNAME', (0,0), (-1,0), 'Helvetica-Bold'),
        ('BOTTOMPADDING', (0,0), (-1,0), 12),
        ('BACKGROUND', (0,1), (-1,-1), colors.beige),
        ('GRID', (0,0), (-1,-1), 1, colors.black)
    ]))
    elements.append(t)
    elements.append(Spacer(1, 20))
    
    # Disclaimer
    disclaimer = ("DISCLAIMER: BodySense is intended for informational and educational purposes only. "
                  "The assessments provided are based on statistical models and do not constitute medical "
                  "advice, diagnosis, or treatment. Always consult a qualified healthcare professional.")
    elements.append(Paragraph(disclaimer, styles['Italic']))

    doc.build(elements)
    
    pdf = buffer.getvalue()
    buffer.close()

    headers = {
        'Content-Disposition': f'attachment; filename="BodySense_Report.pdf"'
    }
    return Response(content=pdf, media_type="application/pdf", headers=headers)
