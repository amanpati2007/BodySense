import warnings
warnings.filterwarnings('ignore')
from main import _run_prediction, _load_model

_load_model("liver")

data = {
    "age": 45, "gender": 1, "total_bilirubin": 1.2, "direct_bilirubin": 0.4,
    "alkaline_phosphotase": 160, "alamine_aminotransferase": 45, 
    "aspartate_aminotransferase": 35, "total_proteins": 6.5,
    "albumin": 3.4, "albumin_globulin_ratio": 1.1
}

res = _run_prediction("liver", data)
print("Risk:", res[0])
print("Confidence:", res[1])
print("Method:", res[2])
print("Top Contributors:", res[3])
print("Explanation:", res[4])
