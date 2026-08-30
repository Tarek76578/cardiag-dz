import platform
import shutil
import subprocess

print("CARDIAG_KAGGLE_WORKER=START")
print(f"PYTHON={platform.python_version()}")
print(f"HOST={platform.platform()}")

nvidia_smi = shutil.which("nvidia-smi")
print(f"NVIDIA_SMI_PRESENT={int(nvidia_smi is not None)}")
if nvidia_smi:
    result = subprocess.run(
        [nvidia_smi, "--query-gpu=name,memory.total,driver_version", "--format=csv,noheader"],
        check=False,
        capture_output=True,
        text=True,
    )
    print(f"NVIDIA_SMI_RC={result.returncode}")
    if result.stdout.strip():
        print("NVIDIA_SMI=" + result.stdout.strip().replace("\n", " | "))

try:
    import torch
except Exception as exc:
    print(f"TORCH_IMPORT_ERROR={type(exc).__name__}: {exc}")
    raise

print(f"TORCH_VERSION={torch.__version__}")
print(f"TORCH_CUDA_VERSION={torch.version.cuda}")
print(f"TORCH_CUDA_AVAILABLE={int(torch.cuda.is_available())}")
print(f"TORCH_CUDA_DEVICE_COUNT={torch.cuda.device_count()}")

if not torch.cuda.is_available() or torch.cuda.device_count() < 1:
    raise RuntimeError("Kaggle requested a GPU, but CUDA is not available in the worker")

for index in range(torch.cuda.device_count()):
    print(
        f"CUDA_DEVICE_{index}="
        f"{torch.cuda.get_device_name(index)} | "
        f"capability={torch.cuda.get_device_capability(index)}"
    )

device = torch.device("cuda:0")
a = torch.randn((1024, 1024), device=device)
b = torch.randn((1024, 1024), device=device)
c = a @ b
print(f"CUDA_TEST_SUM={float(c.sum().item()):.6f}")
print(f"CUDA_MEMORY_ALLOCATED={torch.cuda.memory_allocated(0)}")
print("CARDIAG_KAGGLE_WORKER=OK")
