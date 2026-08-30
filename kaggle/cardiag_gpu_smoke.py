import os
import platform
import subprocess

print("CARDIAG_KAGGLE_WORKER=START")
print(f"PYTHON={platform.python_version()}")
print(f"HOST={platform.platform()}")

try:
    gpu = subprocess.run(
        ["nvidia-smi", "--query-gpu=name,memory.total,driver_version", "--format=csv,noheader"],
        check=True,
        capture_output=True,
        text=True,
    )
    print("GPU=" + gpu.stdout.strip().replace("\n", " | "))
except Exception as exc:
    print(f"GPU_ERROR={type(exc).__name__}: {exc}")
    raise

print("CARDIAG_KAGGLE_WORKER=OK")
