"""Install the kernel solely from the bundle's hash-locked wheelhouse."""

import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


def bootstrap(package, root):
    package, root = Path(package).resolve(), Path(root).absolute()
    root.mkdir(parents=True, mode=0o700, exist_ok=True)
    manifest = json.loads((package / "manifest.json").read_text())
    for name, expected in manifest["sha256"].items():
        target = package / name
        if not target.resolve().is_relative_to(package):
            raise ValueError("Bundle member is outside its folder")
        if hashlib.sha256(target.read_bytes()).hexdigest() != expected:
            raise ValueError("Bundle checksum did not match")
    env = {"PATH": "/usr/bin:/bin", "HOME": str(root), "UV_OFFLINE": "1", "UV_CACHE_DIR": str(root / "cache/uv"), "PYTHONDONTWRITEBYTECODE": "1"}
    uv = str(package / "bin/uv")
    python = str(package / manifest["python"])
    venv = root / "kernel-venv"
    subprocess.run([uv, "venv", str(venv), "--python", python, "--relocatable"], env=env, check=True)
    subprocess.run([
        uv, "pip", "sync", "--python", str(venv / "bin/python"),
        "--no-index", "--find-links", str(package / "wheels"), "--require-hashes",
        str(package / "requirements.lock"),
    ], env=env, check=True)


if __name__ == "__main__":
    bootstrap(Path(__file__).resolve().parent, sys.argv[1])
