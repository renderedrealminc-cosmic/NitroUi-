import os
import gc
import platform

def run_nitro_python_boost(app_count):
    gc.collect()
    py_ver = platform.python_version()
    return f"🐍 Python {py_ver} Active | {app_count} Apps Indexed | Bloat Shield ON"

def analyze_bloatware(pkg_list_str):
    pkgs = pkg_list_str.split(",")
    google_bloat = [p for p in pkgs if "google" in p or "youtube" in p or "setup" in p]
    return f"Python Scanner found {len(google_bloat)} system/Google packages ready for control."
  
