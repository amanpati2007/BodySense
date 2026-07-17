"""
BodySense API — Pydantic Schemas
All input/output models for the prediction endpoints.
"""

from pydantic import BaseModel, Field


# ─── Output ───────────────────────────────────────────────────────────────────
class FeatureContribution(BaseModel):
    feature: str
    contribution: float
    description: str

class PredictionResponse(BaseModel):
    disease: str
    risk: float = Field(ge=0.0, le=100.0, description="Risk score (0–100)")
    confidence: float = Field(ge=0.0, le=1.0, description="Model confidence (0–1)")
    method: str = Field(description="'ml_model' or 'heuristic'")
    top_contributors: list[FeatureContribution] = Field(default_factory=list)
    explanation: str = ""


class ReportRequest(BaseModel):
    disease_name: str
    risk: float
    confidence: float
    method: str
    patient_inputs: dict[str, str]


# ─── Heart Disease ─────────────────────────────────────────────────────────────
class HeartInput(BaseModel):
    age: float = Field(gt=0, description="Age in years")
    sex: float = Field(ge=0, le=1, description="0=female, 1=male")
    chest_pain_type: float = Field(ge=0, le=3, description="0-3 (0=typical angina)")
    resting_bp: float = Field(gt=0, description="Resting blood pressure (mm Hg)")
    cholesterol: float = Field(ge=0, description="Serum cholesterol (mg/dl)")
    fasting_blood_sugar: float = Field(ge=0, le=1, description="Fasting blood sugar > 120 mg/dl (1=true)")
    resting_ecg: float = Field(ge=0, le=2, description="Resting ECG results (0-2)")
    max_heart_rate: float = Field(gt=0, description="Maximum heart rate achieved")
    exercise_angina: float = Field(ge=0, le=1, description="Exercise induced angina (1=yes)")
    oldpeak: float = Field(description="ST depression induced by exercise")
    st_slope: float = Field(ge=0, le=2, description="Slope of peak exercise ST segment (0-2)")


# ─── Diabetes ─────────────────────────────────────────────────────────────────
class DiabetesInput(BaseModel):
    pregnancies: float = Field(ge=0, description="Number of pregnancies")
    glucose: float = Field(ge=0, description="Plasma glucose concentration (mg/dl)")
    blood_pressure: float = Field(ge=0, description="Diastolic blood pressure (mm Hg)")
    skin_thickness: float = Field(ge=0, description="Triceps skinfold thickness (mm)")
    insulin: float = Field(ge=0, description="2-hour serum insulin (μU/ml)")
    bmi: float = Field(ge=0, description="Body mass index (kg/m²)")
    diabetes_pedigree: float = Field(ge=0, description="Diabetes pedigree function")
    age: float = Field(gt=0, description="Age in years")


# ─── Kidney Disease ───────────────────────────────────────────────────────────
class KidneyInput(BaseModel):
    age: float = Field(gt=0)
    blood_pressure: float = Field(ge=0)
    specific_gravity: float = Field(ge=0)
    albumin: float = Field(ge=0)
    sugar: float = Field(ge=0)
    red_blood_cells: float = Field(ge=0, le=1, description="0=normal, 1=abnormal")
    pus_cell: float = Field(ge=0, le=1)
    pus_cell_clumps: float = Field(ge=0, le=1, description="0=not present, 1=present")
    bacteria: float = Field(ge=0, le=1)
    blood_glucose_random: float = Field(ge=0)
    blood_urea: float = Field(ge=0)
    serum_creatinine: float = Field(ge=0)
    sodium: float = Field(ge=0)
    potassium: float = Field(ge=0)
    haemoglobin: float = Field(ge=0)
    packed_cell_volume: float = Field(ge=0)
    wbc_count: float = Field(ge=0)
    rbc_count: float = Field(ge=0)
    hypertension: float = Field(ge=0, le=1)
    diabetes_mellitus: float = Field(ge=0, le=1)
    coronary_artery_disease: float = Field(ge=0, le=1)
    appetite: float = Field(ge=0, le=1, description="0=good, 1=poor")
    pedal_edema: float = Field(ge=0, le=1)
    anemia: float = Field(ge=0, le=1)


# ─── Stroke ───────────────────────────────────────────────────────────────────
class StrokeInput(BaseModel):
    age: float = Field(gt=0)
    hypertension: float = Field(ge=0, le=1)
    heart_disease: float = Field(ge=0, le=1)
    ever_married: float = Field(ge=0, le=1, description="0=no, 1=yes")
    work_type: float = Field(ge=0, le=4, description="0=children 1=govt 2=never_worked 3=private 4=self")
    residence_type: float = Field(ge=0, le=1, description="0=rural, 1=urban")
    avg_glucose_level: float = Field(ge=0)
    bmi: float = Field(ge=0)
    smoking_status: float = Field(ge=0, le=3, description="0=formerly 1=never 2=smokes 3=unknown")


# ─── Parkinson's ──────────────────────────────────────────────────────────────
class ParkinsonInput(BaseModel):
    mdvp_fo: float
    mdvp_fhi: float
    mdvp_flo: float
    mdvp_jitter_percent: float
    mdvp_jitter_abs: float
    mdvp_rap: float
    mdvp_ppq: float
    jitter_ddp: float
    mdvp_shimmer: float
    mdvp_shimmer_db: float
    shimmer_apq3: float
    shimmer_apq5: float
    mdvp_apq: float
    shimmer_dda: float
    nhr: float
    hnr: float
    rpde: float
    dfa: float
    spread1: float
    spread2: float
    d2: float
    ppe: float


# ─── Liver Disease ────────────────────────────────────────────────────────────
class LiverInput(BaseModel):
    age: float = Field(gt=0)
    gender: float = Field(ge=0, le=1, description="0=female, 1=male")
    total_bilirubin: float = Field(ge=0)
    direct_bilirubin: float = Field(ge=0)
    alkaline_phosphotase: float = Field(ge=0)
    alamine_aminotransferase: float = Field(ge=0)
    aspartate_aminotransferase: float = Field(ge=0)
    total_proteins: float = Field(ge=0)
    albumin: float = Field(ge=0)
    albumin_globulin_ratio: float = Field(ge=0)


# ─── Lung Disease ─────────────────────────────────────────────────────────────
class LungInput(BaseModel):
    gender: float = Field(ge=0, le=1, description="0=female, 1=male")
    age: float = Field(gt=0)
    smoking: float = Field(ge=1, le=2, description="1=no, 2=yes")
    yellow_fingers: float = Field(ge=1, le=2)
    anxiety: float = Field(ge=1, le=2)
    peer_pressure: float = Field(ge=1, le=2)
    chronic_disease: float = Field(ge=1, le=2)
    fatigue: float = Field(ge=1, le=2)
    allergy: float = Field(ge=1, le=2)
    wheezing: float = Field(ge=1, le=2)
    alcohol_consuming: float = Field(ge=1, le=2)
    coughing: float = Field(ge=1, le=2)
    shortness_of_breath: float = Field(ge=1, le=2)
    swallowing_difficulty: float = Field(ge=1, le=2)
    chest_pain: float = Field(ge=1, le=2)
