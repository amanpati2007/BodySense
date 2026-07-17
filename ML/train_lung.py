"""
ML Training — Lung Cancer
==========================
Dataset : Lung Cancer Survey Dataset
Source  : Kaggle / GitHub Mirror
License : CC0 Public Domain
Features: 15 survey features
Target  : 1 = lung cancer, 0 = no lung cancer
"""

import logging
import warnings
import json
import urllib.request
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, roc_auc_score, precision_score, recall_score, f1_score
from sklearn.model_selection import StratifiedKFold, GridSearchCV, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.impute import SimpleImputer
import shap

warnings.filterwarnings("ignore")
logging.basicConfig(level=logging.INFO, format="%(levelname)s | %(message)s")
log = logging.getLogger("train_lung")

MODELS_DIR = Path(__file__).parent / "models"
DATASETS_DIR = Path(__file__).parent / "datasets"
MODELS_DIR.mkdir(exist_ok=True)
DATASETS_DIR.mkdir(exist_ok=True)

CSV_PATH = DATASETS_DIR / "lung_cancer.csv"

# 1. Fetch Dataset
URL = "https://raw.githubusercontent.com/Hrishikesh332/Lung-Cancer-Diagnosis-Prediction/main/survey%20lung%20cancer.csv"
log.info("Downloading Lung Cancer dataset from verified mirror...")
try:
    urllib.request.urlretrieve(URL, CSV_PATH)
except Exception as e:
    log.error("Failed to download lung cancer dataset: %s", e)
    raise

df = pd.read_csv(CSV_PATH)

# Normalise column names
df.columns = [c.strip().upper().replace(" ", "_") for c in df.columns]

df["GENDER"] = (df["GENDER"].str.strip().str.upper() == "M").astype(int)
df["LUNG_CANCER"] = (df["LUNG_CANCER"].str.strip().str.upper() == "YES").astype(int)

FEATURE_COLS = [
    "GENDER", "AGE", "SMOKING", "YELLOW_FINGERS", "ANXIETY",
    "PEER_PRESSURE", "CHRONIC_DISEASE", "FATIGUE", "ALLERGY",
    "WHEEZING", "ALCOHOL_CONSUMING", "COUGHING",
    "SHORTNESS_OF_BREATH", "SWALLOWING_DIFFICULTY", "CHEST_PAIN",
]
FEATURE_COLS = [c for c in FEATURE_COLS if c in df.columns]
X = df[FEATURE_COLS].values.astype(float)
y = df["LUNG_CANCER"].values

log.info("Dataset: %d samples, %d features, %.1f%% positive", len(X), X.shape[1], y.mean() * 100)

X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)

# 2. Pipeline & Hyperparameter Tuning
log.info("Starting Hyperparameter Optimization (with class_weight='balanced' to handle class imbalance)...")

pipelines = {
    "LogisticRegression": Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler()),
        ("clf", LogisticRegression(random_state=42, class_weight='balanced', max_iter=1000))
    ]),
    "RandomForest": Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler()),
        ("clf", RandomForestClassifier(random_state=42, class_weight='balanced'))
    ])
}

param_grids = {
    "LogisticRegression": {
        "clf__C": [0.1, 1.0, 10.0]
    },
    "RandomForest": {
        "clf__n_estimators": [50, 100, 200],
        "clf__max_depth": [3, 5, 10]
    }
}

best_score = 0
best_model_name = None
best_model = None

cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)

for name in pipelines.keys():
    grid = GridSearchCV(pipelines[name], param_grids[name], cv=cv, scoring="roc_auc", n_jobs=-1)
    grid.fit(X_train, y_train)
    log.info("  %s - Best AUC: %.4f - Params: %s", name, grid.best_score_, grid.best_params_)
    if grid.best_score_ > best_score:
        best_score = grid.best_score_
        best_model_name = name
        best_model = grid.best_estimator_

log.info("Selected Model: %s (CV AUC = %.4f)", best_model_name, best_score)

# 3. Model Evaluation
y_pred = best_model.predict(X_test)
y_pred_proba = best_model.predict_proba(X_test)[:, 1]

acc = best_model.score(X_test, y_test)
prec = precision_score(y_test, y_pred)
rec = recall_score(y_test, y_pred)
f1 = f1_score(y_test, y_pred)
roc = roc_auc_score(y_test, y_pred_proba)

log.info("Test Metrics: Acc: %.4f | Prec: %.4f | Rec: %.4f | F1: %.4f | AUC: %.4f", acc, prec, rec, f1, roc)

# 4. Feature Importance (SHAP)
log.info("Calculating SHAP Feature Importances...")
X_test_processed = best_model[:-1].transform(X_test)
clf = best_model.named_steps["clf"]

try:
    if best_model_name == "RandomForest":
        explainer = shap.TreeExplainer(clf)
        shap_values = explainer.shap_values(X_test_processed)
        if isinstance(shap_values, list):
            shap_values = shap_values[1]
    else:
        explainer = shap.LinearExplainer(clf, X_test_processed)
        shap_values = explainer.shap_values(X_test_processed)

    vals = np.abs(shap_values).mean(0)
    feature_importance = pd.DataFrame(list(zip(FEATURE_COLS, vals)), columns=['feature', 'importance'])
    feature_importance.sort_values(by=['importance'], ascending=False, inplace=True)
    
    log.info("Top 5 Features by SHAP Importance:")
    for _, row in feature_importance.head(5).iterrows():
        log.info("  - %s: %.4f", row['feature'], row['importance'])
        
    feature_importance.to_csv(MODELS_DIR / "lung_shap_importance.csv", index=False)
except Exception as e:
    log.error("SHAP calculation failed: %s", e)

# 5. Export
joblib.dump(best_model.named_steps["clf"], MODELS_DIR / "lung_model.pkl")
joblib.dump(best_model[:-1], MODELS_DIR / "lung_scaler.pkl")
log.info("Saved: lung_model.pkl, lung_scaler.pkl")
