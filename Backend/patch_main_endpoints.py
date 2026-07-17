import re

content = open('main.py').read()

# Fallback needs to return 5 items
content = content.replace(
    'return heuristic, 0.5, "heuristic", []\n',
    'return heuristic, 0.5, "heuristic", [], ""\n'
)

# Endpoints now receive 5 items from _run_prediction
content = re.sub(
    r'risk, confidence, method, contribs = _run_prediction\((.*?)\)',
    r'risk, confidence, method, contribs, explanation = _run_prediction(\1)',
    content
)

content = re.sub(
    r'return PredictionResponse\(disease=(.*?), risk=risk, confidence=confidence, method=method, top_contributors=contribs\)',
    r'return PredictionResponse(disease=\1, risk=risk, confidence=confidence, method=method, top_contributors=contribs, explanation=explanation)',
    content
)

open('main.py', 'w').write(content)
print("main.py endpoints patched.")
