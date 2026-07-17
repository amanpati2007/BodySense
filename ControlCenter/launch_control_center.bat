@echo off
REM BodySense Control Center Launcher
REM Double-click this file to start the Control Center GUI.

SETLOCAL

SET SCRIPT_DIR=%~dp0
SET PYTHON_EXE=

REM Search for venv python in order of preference
IF EXIST "%SCRIPT_DIR%..\ML\.venv\Scripts\python.exe" (
    SET PYTHON_EXE=%SCRIPT_DIR%..\ML\.venv\Scripts\python.exe
) ELSE IF EXIST "%SCRIPT_DIR%..\.venv\Scripts\python.exe" (
    SET PYTHON_EXE=%SCRIPT_DIR%..\.venv\Scripts\python.exe
) ELSE (
    SET PYTHON_EXE=python
)

REM Install dependencies if needed
"%PYTHON_EXE%" -m pip install -r "%SCRIPT_DIR%requirements.txt" --quiet

REM Launch the Control Center
"%PYTHON_EXE%" "%SCRIPT_DIR%BodySenseControlCenter.py"

ENDLOCAL
