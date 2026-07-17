#!/usr/bin/env python3
"""
ML Training — Run All Models
==============================
Convenience script that runs all 7 training scripts in sequence.

Usage:
    python ML/train_all.py

Each script saves its model and scaler to ML/models/.
Requires Python 3.9+ and the dependencies in ML/requirements.txt.
"""

import subprocess
import sys
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent

SCRIPTS = [
    ("Heart Disease",       "train_heart.py"),
    ("Diabetes",            "train_diabetes.py"),
    ("Kidney Disease",      "train_kidney.py"),
    ("Stroke Risk",         "train_stroke.py"),
    ("Parkinson's Disease", "train_parkinsons.py"),
    ("Liver Disease",       "train_liver.py"),
    ("Lung Cancer",         "train_lung.py"),
]

def main():
    print("=" * 60)
    print("  BodySense - Training All Disease Models")
    print("=" * 60)
    results = []
    for name, script in SCRIPTS:
        print(f"\n>  {name}")
        print("-" * 40)
        start = time.time()
        result = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / script)],
            capture_output=False,
        )
        elapsed = time.time() - start
        status = "OK" if result.returncode == 0 else "FAILED"
        results.append((name, status, f"{elapsed:.1f}s"))
        print(f"   -> {status} ({elapsed:.1f}s)")

    print("\n" + "=" * 60)
    print("  Training Summary")
    print("=" * 60)
    for name, status, elapsed in results:
        print(f"  {status:8s}  {name:25s}  {elapsed}")

    failed = [r for r in results if "FAILED" in r[1]]
    if failed:
        print(f"\n  {len(failed)} script(s) failed. Check logs above.")
        sys.exit(1)
    else:
        print(f"\n  All {len(results)} models trained successfully.")
        print("  Models saved to: ML/models/")

if __name__ == "__main__":
    main()
