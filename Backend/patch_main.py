import sys
content = open('main.py').read()

import re

# Update _run_prediction definition
old_run = '''def _run_prediction(disease: str, features: list[float]) -> tuple[float, float, str]:
    """
    Run model prediction or fall back to a simple heuristic.

    Returns:
        (risk_score 0-100, confidence 0-1, method)
    """
    entry = DISEASE_MODELS[disease]
    model = entry["model"]
    scaler = entry["scaler"]

    if model is not None:
        arr = np.array(features, dtype=np.float32).reshape(1, -1)
        if scaler is not None:
            arr = scaler.transform(arr)
        try:
            proba = model.predict_proba(arr)[0]
            risk_score = float(proba[1]) * 100.0
            confidence = float(max(proba))
            return risk_score, confidence, "ml_model"
        except Exception as e:
            log.error("Model prediction failed for %s: %s", disease, e)

    # Fallback heuristic (age-weighted average of normalised feature magnitudes)
    if features:
        heuristic = min(100.0, max(0.0, float(np.mean(np.abs(features))) * 0.5))
    else:
        heuristic = 50.0
    return heuristic, 0.5, "heuristic"'''

new_run = '''import pandas as pd

def _run_prediction(disease: str, features_dict: dict) -> tuple[float, float, str]:
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
        if scaler is not None:
            try:
                df = scaler.transform(df)
            except Exception as e:
                log.error("Scaler transform failed for %s: %s", disease, e)
        try:
            proba = model.predict_proba(df)[0]
            risk_score = float(proba[1]) * 100.0
            confidence = float(max(proba))
            return risk_score, confidence, "ml_model"
        except Exception as e:
            log.error("Model prediction failed for %s: %s", disease, e)

    # Fallback heuristic (average of numeric values)
    numeric_vals = [float(v) for v in features_dict.values() if isinstance(v, (int, float))]
    if numeric_vals:
        heuristic = min(100.0, max(0.0, float(np.mean(np.abs(numeric_vals))) * 0.5))
    else:
        heuristic = 50.0
    return heuristic, 0.5, "heuristic"'''

content = content.replace(old_run, new_run)

# Predict endpoints
heart_old = '''def predict_heart(data: HeartInput):
    features = [
        data.age, data.sex, data.chest_pain_type, data.resting_bp,
        data.cholesterol, data.fasting_blood_sugar, data.resting_ecg,
        data.max_heart_rate, data.exercise_angina, data.oldpeak,
        data.st_slope,
    ]
    risk, confidence, method = _run_prediction("heart", features)'''

heart_new = '''def predict_heart(data: HeartInput):
    features = {
        "age": data.age, "sex": data.sex, "cp": data.chest_pain_type,
        "trestbps": data.resting_bp, "chol": data.cholesterol,
        "fbs": data.fasting_blood_sugar, "restecg": data.resting_ecg,
        "thalach": data.max_heart_rate, "exang": data.exercise_angina,
        "oldpeak": data.oldpeak, "slope": data.st_slope,
    }
    risk, confidence, method = _run_prediction("heart", features)'''

content = content.replace(heart_old, heart_new)

diabetes_old = '''def predict_diabetes(data: DiabetesInput):
    features = [
        data.pregnancies, data.glucose, data.blood_pressure,
        data.skin_thickness, data.insulin, data.bmi,
        data.diabetes_pedigree, data.age,
    ]
    risk, confidence, method = _run_prediction("diabetes", features)'''

diabetes_new = '''def predict_diabetes(data: DiabetesInput):
    features = {
        "pregnancies": data.pregnancies, "glucose": data.glucose,
        "blood_pressure": data.blood_pressure, "skin_thickness": data.skin_thickness,
        "insulin": data.insulin, "bmi": data.bmi,
        "diabetes_pedigree": data.diabetes_pedigree, "age": data.age,
    }
    risk, confidence, method = _run_prediction("diabetes", features)'''

content = content.replace(diabetes_old, diabetes_new)

kidney_old = '''def predict_kidney(data: KidneyInput):
    features = [
        data.age, data.blood_pressure, data.specific_gravity, data.albumin,
        data.sugar, data.red_blood_cells, data.pus_cell, data.pus_cell_clumps,
        data.bacteria, data.blood_glucose_random, data.blood_urea,
        data.serum_creatinine, data.sodium, data.potassium, data.haemoglobin,
        data.packed_cell_volume, data.wbc_count, data.rbc_count, data.hypertension,
        data.diabetes_mellitus, data.coronary_artery_disease, data.appetite,
        data.pedal_edema, data.anemia,
    ]
    risk, confidence, method = _run_prediction("kidney", features)'''

kidney_new = '''def predict_kidney(data: KidneyInput):
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
    risk, confidence, method = _run_prediction("kidney", features)'''

content = content.replace(kidney_old, kidney_new)

stroke_old = '''def predict_stroke(data: StrokeInput):
    features = [
        data.age, data.hypertension, data.heart_disease, data.ever_married,
        data.work_type, data.residence_type, data.avg_glucose_level,
        data.bmi, data.smoking_status,
    ]
    risk, confidence, method = _run_prediction("stroke", features)'''

stroke_new = '''def predict_stroke(data: StrokeInput):
    features = {
        "age": data.age, "hypertension": data.hypertension, "heart_disease": data.heart_disease,
        "ever_married": data.ever_married, "work_type": data.work_type,
        "Residence_type": data.residence_type, "avg_glucose_level": data.avg_glucose_level,
        "bmi": data.bmi, "smoking_status": data.smoking_status,
    }
    risk, confidence, method = _run_prediction("stroke", features)'''

content = content.replace(stroke_old, stroke_new)

parkinsons_old = '''def predict_parkinsons(data: ParkinsonInput):
    features = [
        data.mdvp_fo, data.mdvp_fhi, data.mdvp_flo, data.mdvp_jitter_percent,
        data.mdvp_jitter_abs, data.mdvp_rap, data.mdvp_ppq, data.jitter_ddp,
        data.mdvp_shimmer, data.mdvp_shimmer_db, data.shimmer_apq3,
        data.shimmer_apq5, data.mdvp_apq, data.shimmer_dda,
        data.nhr, data.hnr, data.rpde, data.dfa, data.spread1, data.spread2,
        data.d2, data.ppe,
    ]
    risk, confidence, method = _run_prediction("parkinsons", features)'''

parkinsons_new = '''def predict_parkinsons(data: ParkinsonInput):
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
    risk, confidence, method = _run_prediction("parkinsons", features)'''

content = content.replace(parkinsons_old, parkinsons_new)

liver_old = '''def predict_liver(data: LiverInput):
    features = [
        data.age, data.gender, data.total_bilirubin, data.direct_bilirubin,
        data.alkaline_phosphotase, data.alamine_aminotransferase,
        data.aspartate_aminotransferase, data.total_proteins, data.albumin,
        data.albumin_globulin_ratio,
    ]
    risk, confidence, method = _run_prediction("liver", features)'''

liver_new = '''def predict_liver(data: LiverInput):
    features = {
        "age": data.age, "gender": data.gender, "total_bilirubin": data.total_bilirubin,
        "direct_bilirubin": data.direct_bilirubin, "alkaline_phosphotase": data.alkaline_phosphotase,
        "alamine_aminotransferase": data.alamine_aminotransferase,
        "aspartate_aminotransferase": data.aspartate_aminotransferase,
        "total_proteins": data.total_proteins, "albumin": data.albumin,
        "albumin_globulin_ratio": data.albumin_globulin_ratio,
    }
    risk, confidence, method = _run_prediction("liver", features)'''

content = content.replace(liver_old, liver_new)

lung_old = '''def predict_lung(data: LungInput):
    features = [
        data.gender, data.age, data.smoking, data.yellow_fingers,
        data.anxiety, data.peer_pressure, data.chronic_disease, data.fatigue,
        data.allergy, data.wheezing, data.alcohol_consuming, data.coughing,
        data.shortness_of_breath, data.swallowing_difficulty, data.chest_pain,
    ]
    risk, confidence, method = _run_prediction("lung", features)'''

lung_new = '''def predict_lung(data: LungInput):
    features = {
        "GENDER": data.gender, "AGE": data.age, "SMOKING": data.smoking,
        "YELLOW_FINGERS": data.yellow_fingers, "ANXIETY": data.anxiety,
        "PEER_PRESSURE": data.peer_pressure, "CHRONIC_DISEASE": data.chronic_disease,
        "FATIGUE": data.fatigue, "ALLERGY": data.allergy, "WHEEZING": data.wheezing,
        "ALCOHOL_CONSUMING": data.alcohol_consuming, "COUGHING": data.coughing,
        "SHORTNESS_OF_BREATH": data.shortness_of_breath,
        "SWALLOWING_DIFFICULTY": data.swallowing_difficulty, "CHEST_PAIN": data.chest_pain,
    }
    risk, confidence, method = _run_prediction("lung", features)'''

content = content.replace(lung_old, lung_new)

legacy_old = '''def predict_legacy(data: HeartInput):
    """Kept for backward compatibility. Use /predict/heart instead."""
    features = [
        data.age, data.sex, data.chest_pain_type, data.resting_bp,
        data.cholesterol, data.fasting_blood_sugar, data.resting_ecg,
        data.max_heart_rate, data.exercise_angina, data.oldpeak,
        data.st_slope,
    ]
    risk, confidence, method = _run_prediction("heart", features)'''

legacy_new = '''def predict_legacy(data: HeartInput):
    """Kept for backward compatibility. Use /predict/heart instead."""
    features = {
        "age": data.age, "sex": data.sex, "cp": data.chest_pain_type,
        "trestbps": data.resting_bp, "chol": data.cholesterol,
        "fbs": data.fasting_blood_sugar, "restecg": data.resting_ecg,
        "thalach": data.max_heart_rate, "exang": data.exercise_angina,
        "oldpeak": data.oldpeak, "slope": data.st_slope,
    }
    risk, confidence, method = _run_prediction("heart", features)'''

content = content.replace(legacy_old, legacy_new)

open('main.py', 'w').write(content)
