"""
BodySense Control Center
========================
A polished desktop GUI for managing the BodySense FastAPI backend during development.

Features:
  - Backend status dashboard (live health polling)
  - ML model loading progress per-disease
  - Start / Stop / Restart backend controls
  - Live log viewer with color-coded output
  - Auto-detected LAN IP
  - Quick links: API Docs, /health, copy URL
  - Settings tab for port and paths

Requirements:
    pip install customtkinter requests psutil

Usage:
    python BodySenseControlCenter.py
"""

import os
import sys
import time
import socket
import subprocess
import threading
import webbrowser
from datetime import datetime
from pathlib import Path

import customtkinter as ctk
import requests
import psutil

# ─── Theme ───────────────────────────────────────────────────────────────────

ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")

ACCENT    = "#3B82F6"        # Electric blue
SUCCESS   = "#22C55E"        # Green
WARNING   = "#F59E0B"        # Amber
DANGER    = "#EF4444"        # Red
BG_CARD   = "#1E293B"        # Slate 800
BG_DARK   = "#0F172A"        # Slate 900
BG_PANEL  = "#1E2D45"        # Slightly lighter
TEXT_DIM  = "#94A3B8"        # Slate 400
TEXT_MAIN = "#F1F5F9"        # Slate 100

DISEASES = ["heart", "diabetes", "kidney", "stroke", "parkinsons", "liver", "lung"]

# ─── Helpers ──────────────────────────────────────────────────────────────────

def get_lan_ip() -> str:
    """Return the best LAN IPv4 address, or '127.0.0.1' as fallback."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def find_venv_python() -> str:
    """Find Python executable in the ML venv, falling back to sys.executable."""
    root = Path(__file__).parent.parent
    candidates = [
        root / "ML" / ".venv" / "Scripts" / "python.exe",
        root / ".venv" / "Scripts" / "python.exe",
    ]
    for c in candidates:
        if c.exists():
            return str(c)
    return sys.executable


def find_backend_dir() -> Path:
    return Path(__file__).parent.parent / "Backend"


# ─── App ──────────────────────────────────────────────────────────────────────

class BodySenseControlCenter(ctk.CTk):
    def __init__(self):
        super().__init__()

        self.title("BodySense Control Center")
        self.geometry("1000x700")
        self.minsize(860, 600)
        self.configure(fg_color=BG_DARK)

        # State
        self.backend_process: subprocess.Popen | None = None
        self.lan_ip = get_lan_ip()
        self.port = 8000
        self.health_data: dict = {}
        self.poll_active = True
        self.start_time: float | None = None
        self._log_lines = 0

        self._build_ui()
        self._start_polling()

    # ── UI Construction ───────────────────────────────────────────────────────

    def _build_ui(self):
        # Title bar
        title_frame = ctk.CTkFrame(self, fg_color=BG_CARD, corner_radius=0, height=60)
        title_frame.pack(fill="x", side="top")
        title_frame.pack_propagate(False)

        ctk.CTkLabel(
            title_frame, text="❤  BodySense Control Center",
            font=ctk.CTkFont(family="Segoe UI", size=18, weight="bold"),
            text_color=TEXT_MAIN
        ).pack(side="left", padx=24, pady=16)

        self.time_label = ctk.CTkLabel(
            title_frame, text="", font=ctk.CTkFont(size=12), text_color=TEXT_DIM
        )
        self.time_label.pack(side="right", padx=24)

        # Tab view
        self.tabs = ctk.CTkTabview(self, fg_color=BG_DARK, segmented_button_fg_color=BG_CARD)
        self.tabs.pack(fill="both", expand=True, padx=16, pady=(8, 16))

        self.tabs.add("Dashboard")
        self.tabs.add("Logs")
        self.tabs.add("Settings")

        self._build_dashboard(self.tabs.tab("Dashboard"))
        self._build_logs(self.tabs.tab("Logs"))
        self._build_settings(self.tabs.tab("Settings"))

        # Tick the clock
        self._tick_clock()

    def _build_dashboard(self, parent):
        parent.configure(fg_color=BG_DARK)

        # Left column: Status + Controls
        left = ctk.CTkFrame(parent, fg_color="transparent", width=340)
        left.pack(side="left", fill="y", padx=(0, 12), pady=0)
        left.pack_propagate(False)

        # ── Status Card ──
        status_card = ctk.CTkFrame(left, fg_color=BG_CARD, corner_radius=16)
        status_card.pack(fill="x", pady=(0, 12))

        ctk.CTkLabel(
            status_card, text="Backend Status",
            font=ctk.CTkFont(size=11, weight="bold"), text_color=TEXT_DIM
        ).pack(anchor="w", padx=16, pady=(14, 4))

        self.status_dot = ctk.CTkLabel(
            status_card, text="● Offline",
            font=ctk.CTkFont(size=22, weight="bold"), text_color=DANGER
        )
        self.status_dot.pack(anchor="w", padx=16)

        self.status_sub = ctk.CTkLabel(
            status_card, text="Backend is not running",
            font=ctk.CTkFont(size=12), text_color=TEXT_DIM
        )
        self.status_sub.pack(anchor="w", padx=16, pady=(2, 6))

        # Info grid
        info_frame = ctk.CTkFrame(status_card, fg_color="transparent")
        info_frame.pack(fill="x", padx=16, pady=(4, 14))

        self._info_rows = {}
        for label, key in [
            ("Backend URL", "url"),
            ("LAN IP", "ip"),
            ("Port", "port"),
            ("Uptime", "uptime"),
            ("Predictions Served", "predictions"),
            ("Version", "version"),
        ]:
            row = ctk.CTkFrame(info_frame, fg_color="transparent")
            row.pack(fill="x", pady=2)
            ctk.CTkLabel(row, text=label, font=ctk.CTkFont(size=11), text_color=TEXT_DIM, width=130, anchor="w").pack(side="left")
            val_label = ctk.CTkLabel(row, text="—", font=ctk.CTkFont(size=11, weight="bold"), text_color=TEXT_MAIN, anchor="w")
            val_label.pack(side="left")
            self._info_rows[key] = val_label

        self._update_info_static()

        # ── Controls ──
        ctrl_card = ctk.CTkFrame(left, fg_color=BG_CARD, corner_radius=16)
        ctrl_card.pack(fill="x", pady=(0, 12))

        ctk.CTkLabel(
            ctrl_card, text="Controls",
            font=ctk.CTkFont(size=11, weight="bold"), text_color=TEXT_DIM
        ).pack(anchor="w", padx=16, pady=(14, 8))

        btn_frame = ctk.CTkFrame(ctrl_card, fg_color="transparent")
        btn_frame.pack(fill="x", padx=12, pady=(0, 14))

        self.start_btn = ctk.CTkButton(
            btn_frame, text="▶  Start Backend", fg_color=SUCCESS, hover_color="#16A34A",
            font=ctk.CTkFont(size=13, weight="bold"), height=40, corner_radius=10,
            command=self._start_backend
        )
        self.start_btn.pack(fill="x", pady=(0, 6))

        self.stop_btn = ctk.CTkButton(
            btn_frame, text="■  Stop Backend", fg_color=DANGER, hover_color="#DC2626",
            font=ctk.CTkFont(size=13, weight="bold"), height=40, corner_radius=10,
            command=self._stop_backend, state="disabled"
        )
        self.stop_btn.pack(fill="x", pady=(0, 6))

        self.restart_btn = ctk.CTkButton(
            btn_frame, text="↺  Restart Backend", fg_color=WARNING, hover_color="#D97706",
            text_color="#0F172A", font=ctk.CTkFont(size=13, weight="bold"), height=40,
            corner_radius=10, command=self._restart_backend, state="disabled"
        )
        self.restart_btn.pack(fill="x", pady=(0, 6))

        # Quick links
        links_frame = ctk.CTkFrame(ctrl_card, fg_color="transparent")
        links_frame.pack(fill="x", padx=12, pady=(0, 14))

        ctk.CTkButton(
            links_frame, text="📄  API Docs", fg_color=BG_PANEL, hover_color=BG_DARK,
            font=ctk.CTkFont(size=12), height=32, corner_radius=8,
            command=lambda: webbrowser.open(f"http://localhost:{self.port}/docs")
        ).pack(side="left", fill="x", expand=True, padx=(0, 4))

        ctk.CTkButton(
            links_frame, text="❤  /health", fg_color=BG_PANEL, hover_color=BG_DARK,
            font=ctk.CTkFont(size=12), height=32, corner_radius=8,
            command=lambda: webbrowser.open(f"http://localhost:{self.port}/health")
        ).pack(side="left", fill="x", expand=True, padx=(4, 0))

        ctk.CTkButton(
            ctrl_card, text="📋  Copy Backend URL", fg_color="transparent", hover_color=BG_PANEL,
            font=ctk.CTkFont(size=12), height=28, corner_radius=8,
            command=self._copy_url, border_width=1, border_color=BG_PANEL
        ).pack(fill="x", padx=12, pady=(0, 14))

        # Right column: ML Model Progress
        right = ctk.CTkFrame(parent, fg_color=BG_CARD, corner_radius=16)
        right.pack(side="left", fill="both", expand=True)

        ctk.CTkLabel(
            right, text="ML Model Status",
            font=ctk.CTkFont(size=11, weight="bold"), text_color=TEXT_DIM
        ).pack(anchor="w", padx=16, pady=(14, 4))

        ctk.CTkLabel(
            right, text="7 disease-specific models",
            font=ctk.CTkFont(size=12), text_color=TEXT_DIM
        ).pack(anchor="w", padx=16, pady=(0, 12))

        self._model_bars: dict[str, tuple] = {}
        models_frame = ctk.CTkScrollableFrame(right, fg_color="transparent", height=420)
        models_frame.pack(fill="both", expand=True, padx=8, pady=(0, 12))

        disease_labels = {
            "heart":      ("❤  Heart Disease",       Color(0xFFE53935)),
            "diabetes":   ("💧  Diabetes",            Color(0xFF1E88E5)),
            "kidney":     ("🏥  Chronic Kidney",      Color(0xFF8E24AA)),
            "stroke":     ("⚡  Stroke Risk",         Color(0xFFFB8C00)),
            "parkinsons": ("♿  Parkinson's Disease", Color(0xFF6D4C41)),
            "liver":      ("🔬  Liver Disease",       Color(0xFF43A047)),
            "lung":       ("🌬  Lung Cancer",         Color(0xFF00ACC1)),
        }

        for disease, (label, _) in disease_labels.items():
            card = ctk.CTkFrame(models_frame, fg_color=BG_PANEL, corner_radius=12)
            card.pack(fill="x", pady=5, padx=4)

            row = ctk.CTkFrame(card, fg_color="transparent")
            row.pack(fill="x", padx=12, pady=(10, 4))

            ctk.CTkLabel(
                row, text=label, font=ctk.CTkFont(size=13, weight="bold"), text_color=TEXT_MAIN
            ).pack(side="left")

            status_lbl = ctk.CTkLabel(
                row, text="Not Loaded", font=ctk.CTkFont(size=11), text_color=TEXT_DIM
            )
            status_lbl.pack(side="right")

            bar = ctk.CTkProgressBar(card, height=6, corner_radius=3, fg_color=BG_CARD, progress_color=SUCCESS)
            bar.set(0)
            bar.pack(fill="x", padx=12, pady=(2, 10))

            self._model_bars[disease] = (bar, status_lbl)

    def _build_logs(self, parent):
        parent.configure(fg_color=BG_DARK)

        toolbar = ctk.CTkFrame(parent, fg_color=BG_CARD, corner_radius=10, height=40)
        toolbar.pack(fill="x", pady=(0, 8))
        toolbar.pack_propagate(False)

        ctk.CTkLabel(toolbar, text="Live Backend Logs", font=ctk.CTkFont(size=12, weight="bold"), text_color=TEXT_MAIN).pack(side="left", padx=16, pady=8)

        ctk.CTkButton(
            toolbar, text="Clear", fg_color=BG_PANEL, hover_color=BG_DARK,
            font=ctk.CTkFont(size=11), height=26, width=60, corner_radius=6,
            command=self._clear_logs
        ).pack(side="right", padx=12, pady=6)

        self.log_box = ctk.CTkTextbox(
            parent, fg_color=BG_CARD, text_color=TEXT_MAIN,
            font=ctk.CTkFont(family="Consolas", size=12),
            corner_radius=10, state="disabled"
        )
        self.log_box.pack(fill="both", expand=True)
        self.log_box.tag_config("info",    foreground="#94A3B8")
        self.log_box.tag_config("warn",    foreground="#F59E0B")
        self.log_box.tag_config("error",   foreground="#EF4444")
        self.log_box.tag_config("success", foreground="#22C55E")
        self.log_box.tag_config("system",  foreground="#818CF8")

    def _build_settings(self, parent):
        parent.configure(fg_color=BG_DARK)

        card = ctk.CTkFrame(parent, fg_color=BG_CARD, corner_radius=16)
        card.pack(fill="x", padx=4, pady=4)

        ctk.CTkLabel(card, text="Settings", font=ctk.CTkFont(size=14, weight="bold"), text_color=TEXT_MAIN).pack(anchor="w", padx=16, pady=(14, 8))

        def row(label, default, attr):
            f = ctk.CTkFrame(card, fg_color="transparent")
            f.pack(fill="x", padx=16, pady=4)
            ctk.CTkLabel(f, text=label, font=ctk.CTkFont(size=12), text_color=TEXT_DIM, width=200, anchor="w").pack(side="left")
            e = ctk.CTkEntry(f, height=32, corner_radius=8, font=ctk.CTkFont(size=12))
            e.insert(0, default)
            e.pack(side="left", fill="x", expand=True)
            setattr(self, attr, e)

        row("Backend Port", str(self.port), "port_entry")
        row("Detected LAN IP", self.lan_ip, "ip_entry")

        ctk.CTkButton(
            card, text="Apply Settings", fg_color=ACCENT, hover_color="#2563EB",
            font=ctk.CTkFont(size=13, weight="bold"), height=36, corner_radius=10,
            command=self._apply_settings
        ).pack(anchor="e", padx=16, pady=(8, 16))

    # ── State Updates ─────────────────────────────────────────────────────────

    def _update_info_static(self):
        self._info_rows["url"].configure(text=f"http://{self.lan_ip}:{self.port}/")
        self._info_rows["ip"].configure(text=self.lan_ip)
        self._info_rows["port"].configure(text=str(self.port))

    def _update_dashboard(self, data: dict | None):
        if data is None:
            self.status_dot.configure(text="● Offline", text_color=DANGER)
            self.status_sub.configure(text="Backend is not running or unreachable")
            self._info_rows["uptime"].configure(text="—")
            self._info_rows["predictions"].configure(text="—")
            self._info_rows["version"].configure(text="—")
            for bar, lbl in self._model_bars.values():
                bar.set(0)
                lbl.configure(text="Not Loaded", text_color=DANGER)
            return

        status = data.get("status", "unknown")
        models: dict = data.get("models", {})
        loaded_count = sum(1 for v in models.values() if v == "loaded")
        total = len(models) or 1
        uptime_s = data.get("uptime_seconds", 0)
        preds = data.get("predictions_served", 0)
        version = data.get("version", "—")

        if status == "healthy":
            self.status_dot.configure(text=f"● Online  {loaded_count}/{total} models", text_color=SUCCESS)
            self.status_sub.configure(text="All models loaded — backend ready")
        else:
            self.status_dot.configure(text=f"● Partial  {loaded_count}/{total} models", text_color=WARNING)
            self.status_sub.configure(text="Some models unavailable (heuristic fallback active)")

        uptime_str = self._format_uptime(uptime_s)
        self._info_rows["uptime"].configure(text=uptime_str)
        self._info_rows["predictions"].configure(text=str(preds))
        self._info_rows["version"].configure(text=version)

        for disease, (bar, lbl) in self._model_bars.items():
            state = models.get(disease, "unknown")
            if state == "loaded":
                bar.set(1)
                bar.configure(progress_color=SUCCESS)
                lbl.configure(text="✓ Loaded", text_color=SUCCESS)
            else:
                bar.set(0.3)
                bar.configure(progress_color=WARNING)
                lbl.configure(text="⚠ Heuristic", text_color=WARNING)

    @staticmethod
    def _format_uptime(seconds: float) -> str:
        s = int(seconds)
        h, rem = divmod(s, 3600)
        m, sc = divmod(rem, 60)
        if h > 0:
            return f"{h}h {m}m {sc}s"
        if m > 0:
            return f"{m}m {sc}s"
        return f"{sc}s"

    # ── Polling ───────────────────────────────────────────────────────────────

    def _start_polling(self):
        def poll():
            while self.poll_active:
                try:
                    r = requests.get(
                        f"http://localhost:{self.port}/health", timeout=3
                    )
                    if r.status_code == 200:
                        data = r.json()
                        self.after(0, lambda d=data: self._update_dashboard(d))
                    else:
                        self.after(0, lambda: self._update_dashboard(None))
                except Exception:
                    self.after(0, lambda: self._update_dashboard(None))
                time.sleep(5)

        threading.Thread(target=poll, daemon=True).start()

    # ── Logging ───────────────────────────────────────────────────────────────

    def _log(self, message: str, tag: str = "info"):
        ts = datetime.now().strftime("%H:%M:%S")
        self.log_box.configure(state="normal")
        self.log_box.insert("end", f"[{ts}] {message}\n", tag)
        self.log_box.see("end")
        self.log_box.configure(state="disabled")
        self._log_lines += 1
        if self._log_lines > 2000:
            self._clear_logs()

    def _clear_logs(self):
        self.log_box.configure(state="normal")
        self.log_box.delete("1.0", "end")
        self.log_box.configure(state="disabled")
        self._log_lines = 0

    def _pipe_output(self, proc: subprocess.Popen):
        """Read subprocess stdout/stderr in a background thread and forward to log."""
        def reader(stream, tag):
            for line in iter(stream.readline, ""):
                if line:
                    text = line.rstrip()
                    resolved_tag = tag
                    lower = text.lower()
                    if "error" in lower or "failed" in lower:
                        resolved_tag = "error"
                    elif "warn" in lower:
                        resolved_tag = "warn"
                    elif "ready" in lower or "started" in lower or "loaded" in lower:
                        resolved_tag = "success"
                    self.after(0, lambda t=text, tg=resolved_tag: self._log(t, tg))

        threading.Thread(target=reader, args=(proc.stdout, "info"), daemon=True).start()
        threading.Thread(target=reader, args=(proc.stderr, "warn"), daemon=True).start()

    # ── Backend Control ───────────────────────────────────────────────────────

    def _start_backend(self):
        if self.backend_process and self.backend_process.poll() is None:
            self._log("Backend is already running.", "warn")
            return

        self._log("Starting BodySense backend...", "system")
        self.start_btn.configure(state="disabled")

        backend_dir = find_backend_dir()
        python = find_venv_python()

        if not backend_dir.exists():
            self._log(f"Backend directory not found: {backend_dir}", "error")
            self.start_btn.configure(state="normal")
            return

        cmd = [
            python, "-m", "uvicorn", "main:app",
            "--host", "0.0.0.0",
            "--port", str(self.port),
            "--log-level", "info"
        ]
        self._log(f"Command: {' '.join(cmd)}", "system")
        self._log(f"Working dir: {backend_dir}", "system")

        try:
            self.backend_process = subprocess.Popen(
                cmd,
                cwd=str(backend_dir),
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
            )
            self.start_time = time.time()
            self._pipe_output(self.backend_process)
            self.stop_btn.configure(state="normal")
            self.restart_btn.configure(state="normal")
            self._log("Backend process started. Waiting for health check...", "system")
            self.tabs.set("Logs")
        except Exception as e:
            self._log(f"Failed to start backend: {e}", "error")
            self.start_btn.configure(state="normal")

    def _stop_backend(self):
        if not self.backend_process:
            return
        self._log("Stopping backend...", "system")
        try:
            parent = psutil.Process(self.backend_process.pid)
            for child in parent.children(recursive=True):
                child.terminate()
            parent.terminate()
        except Exception:
            self.backend_process.terminate()

        self.backend_process = None
        self.start_btn.configure(state="normal")
        self.stop_btn.configure(state="disabled")
        self.restart_btn.configure(state="disabled")
        self._log("Backend stopped.", "warn")

    def _restart_backend(self):
        self._log("Restarting backend...", "system")
        self._stop_backend()
        self.after(1500, self._start_backend)

    # ── Misc ──────────────────────────────────────────────────────────────────

    def _copy_url(self):
        url = f"http://{self.lan_ip}:{self.port}/"
        self.clipboard_clear()
        self.clipboard_append(url)
        self._log(f"Copied to clipboard: {url}", "system")

    def _apply_settings(self):
        try:
            self.port = int(self.port_entry.get())
        except ValueError:
            self._log("Invalid port number.", "error")
            return
        self.lan_ip = self.ip_entry.get().strip() or get_lan_ip()
        self._update_info_static()
        self._log(f"Settings applied: port={self.port}, LAN IP={self.lan_ip}", "system")

    def _tick_clock(self):
        self.time_label.configure(text=datetime.now().strftime("%A, %d %b %Y  %H:%M:%S"))
        self.after(1000, self._tick_clock)

    def destroy(self):
        self.poll_active = False
        if self.backend_process:
            self._stop_backend()
        super().destroy()


# ─── Shim for missing Color usage in non-Compose Python ──────────────────────

class Color:
    """Dummy shim so the disease color constants parse (not used in CTk)."""
    def __init__(self, _): pass


# ─── Entry point ──────────────────────────────────────────────────────────────

if __name__ == "__main__":
    app = BodySenseControlCenter()
    app.mainloop()
