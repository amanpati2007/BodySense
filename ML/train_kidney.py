"""
ML Training — Chronic Kidney Disease
======================================
Dataset : UCI Chronic Kidney Disease
Source  : UCI Machine Learning Repository (ID 336)
License : CC BY 4.0
Features: 24 clinical features
Target  : 1 = CKD present, 0 = not CKD
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
from sklearn.preprocessing import StandardScaler, OrdinalEncoder
from sklearn.compose import ColumnTransformer
import shap

warnings.filterwarnings("ignore")
logging.basicConfig(level=logging.INFO, format="%(levelname)s | %(message)s")
log = logging.getLogger("train_kidney")

MODELS_DIR = Path(__file__).parent / "models"
MODELS_DIR.mkdir(exist_ok=True)

# 1. Fetch Dataset
log.info("Downloading UCI Chronic Kidney Disease dataset (ID 336)...")
kidney = fetch_ucirepo(id=336)
df = kidney.data.features.copy()

# The target might have \t characters in UCI, clean it
targets = kidney.data.targets.copy()
if targets.iloc[:, 0].dtype == object:
    targets.iloc[:, 0] = targets.iloc[:, 0].str.strip().str.replace("\t", "")
    
df["target"] = targets.iloc[:, 0].map({"ckd": 1, "notckd": 0})
df.dropna(subset=["target"], inplace=True)

CAT_COLS = ["rbc", "pc", "pcc", "ba", "htn", "dm", "cad", "appet", "pe", "ane"]
NUM_COLS = ["age", "bp", "sg", "al", "su", "bgr", "bu", "sc", "sod", "pot", "hemo", "pcv", "wbcc", "rbcc"]

for col in df.columns:
    if df[col].dtype == object:
        df[col] = df[col].str.strip().str.replace("\t", "")
        # replace '?' with nan
        df[col] = df[col].replace("?", np.nan)

for col in NUM_COLS:
    df[col] = pd.to_numeric(df[col], errors="coerce")

FEATURE_COLS = NUM_COLS + CAT_COLS
X = df[FEATURE_COLS]
y = df["target"].astype(int)

log.info("Dataset: %d samples, %d features, %.1f%% positive", len(X), X.shape[1], y.mean() * 100)

X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)

# 2. Preprocessing & Pipeline
log.info("Starting Hyperparameter Optimization...")

# We use ColumnTransformer to handle num and cat separately
numeric_transformer = Pipeline(steps=[
    ('imputer', SimpleImputer(strategy='median')),
    ('scaler', StandardScaler())
])

categorical_transformer = Pipeline(steps=[
    ('imputer', SimpleImputer(strategy='most_frequent')),
    ('encoder', OrdinalEncoder(handle_unknown='use_encoded_value', unknown_value=-1))
])

preprocessor = ColumnTransformer(
    transformers=[
        ('num', numeric_transformer, NUM_COLS),
        ('cat', categorical_transformer, CAT_COLS)
    ])

pipelines = {
    "LogisticRegression": Pipeline([
        ("preprocessor", preprocessor),
        ("clf", LogisticRegression(random_state=42, max_iter=1000))
    ]),
    "RandomForest": Pipeline([
        ("preprocessor", preprocessor),
        ("clf", RandomForestClassifier(random_state=42))
    ]),
    "GradientBoosting": Pipeline([
        ("preprocessor", preprocessor),
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
X_test_processed = best_model.named_steps["preprocessor"].transform(X_test)
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
    # The columns from preprocessor are NUM_COLS + CAT_COLS in exact order
    out_cols = NUM_COLS + CAT_COLS
    feature_importance = pd.DataFrame(list(zip(out_cols, vals)), columns=['feature', 'importance'])
    feature_importance.sort_values(by=['importance'], ascending=False, inplace=True)
    
    log.info("Top 5 Features by SHAP Importance:")
    for _, row in feature_importance.head(5).iterrows():
        log.info("  - %s: %.4f", row['feature'], row['importance'])
        
    feature_importance.to_csv(MODELS_DIR / "kidney_shap_importance.csv", index=False)
except Exception as e:
    log.error("SHAP calculation failed: %s", e)

# 5. Export
joblib.dump(best_model.named_steps["clf"], MODELS_DIR / "kidney_model.pkl")
joblib.dump(best_model.named_steps["preprocessor"], MODELS_DIR / "kidney_scaler.pkl")
log.info("Saved: kidney_model.pkl, kidney_scaler.pkl")
