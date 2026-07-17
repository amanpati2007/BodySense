"""
ML Training — Liver Disease
============================
Dataset : UCI Indian Liver Patient Dataset (ILPD)
Source  : UCI Machine Learning Repository (ID 225)
License : CC BY 4.0
Features: 10 clinical features
Target  : 1 = liver disease, 0 = no liver disease
"""

import logging
import warnings
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from ucimlrepo import fetch_ucirepo

from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, roc_auc_score, precision_score, recall_score, f1_score
from sklearn.model_selection import StratifiedKFold, GridSearchCV, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import StandardScaler
import shap

warnings.filterwarnings("ignore")
logging.basicConfig(level=logging.INFO, format="%(levelname)s | %(message)s")
log = logging.getLogger("train_liver")

MODELS_DIR = Path(__file__).parent / "models"
MODELS_DIR.mkdir(exist_ok=True)

# 1. Fetch Dataset
log.info("Downloading UCI ILPD (Liver) dataset (ID 225)...")
ilpd = fetch_ucirepo(id=225)

df = ilpd.data.features.copy()
# Note: target in this dataset is 1 = Liver Patient, 2 = Non-Liver Patient
# Convert to 1 = Liver Disease, 0 = No Liver Disease
df["target"] = (ilpd.data.targets.iloc[:, 0] == 1).astype(int)

# Map features to Backend Expected Names
# Original features from UCI: ['Age', 'Gender', 'TB', 'DB', 'Alkphos', 'Sgpt', 'Sgot', 'TP', 'ALB', 'A/G Ratio']
FEATURE_COLS = ["age", "gender", "total_bilirubin", "direct_bilirubin",
                "alkaline_phosphotase", "alamine_aminotransferase",
                "aspartate_aminotransferase", "total_proteins",
                "albumin", "albumin_globulin_ratio"]
df.columns = FEATURE_COLS + ["target"]

df["gender"] = (df["gender"] == "Male").astype(int)

X = df[FEATURE_COLS]
y = df["target"]

log.info("Dataset: %d samples, %d features, %.1f%% positive", len(X), X.shape[1], y.mean() * 100)

X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)

# 2. Pipeline & Hyperparameter Tuning
log.info("Starting Hyperparameter Optimization...")

pipelines = {
    "LogisticRegression": Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler()),
        ("clf", LogisticRegression(random_state=42, max_iter=1000))
    ]),
    "RandomForest": Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler()),
        ("clf", RandomForestClassifier(random_state=42))
    ]),
    "GradientBoosting": Pipeline([
        ("imputer", SimpleImputer(strategy="median")),
        ("scaler", StandardScaler()),
        ("clf", GradientBoostingClassifier(random_state=42))
    ])
}

param_grids = {
    "LogisticRegression": {
        "clf__C": [0.1, 1.0, 10.0]
    },
    "RandomForest": {
        "clf__n_estimators": [50, 100, 200],
        "clf__max_depth": [None, 5, 10]
    },
    "GradientBoosting": {
        "clf__n_estimators": [50, 100, 200],
        "clf__learning_rate": [0.01, 0.1, 0.2]
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
    if best_model_name in ["RandomForest", "GradientBoosting"]:
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
        
    feature_importance.to_csv(MODELS_DIR / "liver_shap_importance.csv", index=False)
except Exception as e:
    log.error("SHAP calculation failed: %s", e)

# 5. Export
joblib.dump(best_model.named_steps["clf"], MODELS_DIR / "liver_model.pkl")
joblib.dump(best_model[:-1], MODELS_DIR / "liver_scaler.pkl")
log.info("Saved: liver_model.pkl, liver_scaler.pkl")
