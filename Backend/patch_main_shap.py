import sys
import re

content = open('main.py').read()

old_run = '''    if model is not None:
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
            return risk_score, confidence, "ml_model"'''

new_run = '''    if model is not None:
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
                    import numpy as np
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
            except Exception as e:
                log.error("SHAP extraction failed for %s: %s", disease, e)

            return risk_score, confidence, "ml_model", contributions
'''

content = content.replace(old_run, new_run)

# Now update the return signature
old_sig = '''def _run_prediction(disease: str, features_dict: dict) -> tuple[float, float, str]:'''
new_sig = '''def _run_prediction(disease: str, features_dict: dict) -> tuple[float, float, str, list]:'''
content = content.replace(old_sig, new_sig)

# And fallback return
old_fallback = '''    return heuristic, 0.5, "heuristic"'''
new_fallback = '''    return heuristic, 0.5, "heuristic", []'''
content = content.replace(old_fallback, new_fallback)

# Now update endpoints to expect 4 returns and set top_contributors
# Use regex to find `risk, confidence, method = _run_prediction("...", features)`
# and replace with `risk, confidence, method, contribs = ...`
# and `return PredictionResponse(..., method=method)`
# with `return PredictionResponse(..., method=method, top_contributors=contribs)`

content = re.sub(
    r'risk, confidence, method = _run_prediction\((.*?)\)',
    r'risk, confidence, method, contribs = _run_prediction(\1)',
    content
)

content = re.sub(
    r'return PredictionResponse\(disease=(.*?), risk=risk, confidence=confidence, method=method\)',
    r'return PredictionResponse(disease=\1, risk=risk, confidence=confidence, method=method, top_contributors=contribs)',
    content
)

open('main.py', 'w').write(content)
print("main.py patched.")
