# BodySense Backend

This is the FastAPI backend for the BodySense Android app.

## Setup
1. Create a virtual environment inside the ML folder: `python -m venv ML/.venv`
2. Run the `start_backend.ps1` or `start_backend.bat` scripts to start the server.

## Endpoints
- `GET /`: Returns the running status
- `GET /health`: Returns the health of the API and ML model
- `POST /predict`: Predicts the heart health risk using the ML model. Requires JSON payload.
