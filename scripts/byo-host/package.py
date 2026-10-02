#!/usr/bin/env python3
"""Build a deterministic BYO host archive from already verified local inputs.

The supplied digest must come from the reviewed upstream artifact, not be learned
from this tool's output. No downloads, publication or binary execution occurs.
"""

import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import sys
import tarfile

BUNDLE_VERSION = "1.0.0"
OPENCODE_VERSION = "1.18.32"


def package(binary, expected_digest, architecture, output):
    binary = Path(binary)
    output = Path(output)
    if not re.fullmatch(r"[0-9a-f]{64}", expected_digest):
        raise ValueError("invalidChecksum")
    content = binary.read_bytes()
    if hashlib.sha256(content).hexdigest() != expected_digest:
        raise ValueError("checksumMismatch")
    machine = {"amd64": 62, "arm64": 183}.get(architecture)
    if (
        machine is None
        or len(content) < 64
        or content[:6] != b"\x7fELF\x02\x01"
        or struct.unpack("<H", content[18:20])[0] != machine
    ):
        raise ValueError("unsupportedBinary")
    root = Path(__file__).resolve().parent
    if output.resolve() in {
        binary.resolve(),
        root / "install.sh",
        root / "supervisor.py",
    }:
        raise ValueError("invalidOutput")
    files = {
        "opencode": content,
        "supervisor.py": (root / "supervisor.py").read_bytes(),
        "install.sh": (root / "install.sh").read_bytes(),
    }
    manifest = {
        "schemaVersion": 1,
        "bundleVersion": BUNDLE_VERSION,
        "openCodeVersion": OPENCODE_VERSION,
        "architecture": architecture,
        "port": 4096,
        "files": {
            name: hashlib.sha256(data).hexdigest() for name, data in files.items()
        },
    }
    manifest_bytes = (
        json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n"
    ).encode()
    files["manifest.json"] = manifest_bytes
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as target:
        with gzip.GzipFile(
            fileobj=target, mode="wb", filename="", mtime=0, compresslevel=9
        ) as compressed:
            with tarfile.open(
                fileobj=compressed, mode="w", format=tarfile.USTAR_FORMAT
            ) as archive:
                for name in sorted(files):
                    entry = tarfile.TarInfo(name)
                    entry.size = len(files[name])
                    entry.mode = 0o600 if name == "manifest.json" else 0o700
                    entry.uid = entry.gid = entry.mtime = 0
                    entry.uname = entry.gname = ""
                    archive.addfile(entry, io.BytesIO(files[name]))
    return dict(
        manifest,
        archiveSha256=hashlib.sha256(output.read_bytes()).hexdigest(),
        manifestSha256=hashlib.sha256(manifest_bytes).hexdigest(),
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--opencode", required=True)
    parser.add_argument("--opencode-sha256", required=True)
    parser.add_argument("--architecture", required=True, choices=("amd64", "arm64"))
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        print(
            json.dumps(
                package(
                    args.opencode, args.opencode_sha256, args.architecture, args.output
                ),
                sort_keys=True,
            )
        )
        return 0
    except ValueError as error:
        print(json.dumps({"error": str(error)}))
    except Exception:
        print(json.dumps({"error": "packageFailed"}))
    return 1


if __name__ == "__main__":
    sys.exit(main())
