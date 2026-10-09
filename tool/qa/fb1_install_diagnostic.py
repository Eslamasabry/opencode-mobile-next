"""Bounded adb install observations; never export raw stdout/stderr."""

import re
import subprocess
import tempfile


def project(returncode, stdout, stderr):
    text = (stdout + b"\n" + stderr).decode("utf-8", errors="replace")
    error = re.search(
        r"Failure \[(INSTALL_(?:FAILED|PARSE_FAILED)_[A-Z0-9_]{1,70})(?=[:\]])", text
    )
    native = re.search(r"\bres=(-?\d{1,6})\b", text)
    return {
        "returnCode": returncode,
        "success": returncode == 0
        and re.search(r"^Success\s*$", text, re.M) is not None,
        "androidError": error[1] if error else None,
        "nativeResult": int(native[1]) if native else None,
    }


def install(serial, apk):
    with tempfile.TemporaryFile() as stdout, tempfile.TemporaryFile() as stderr:
        try:
            completed = subprocess.run(
                ["adb", "-s", serial, "install", str(apk)],
                stdout=stdout,
                stderr=stderr,
                timeout=180,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            return {
                "success": False,
                "transportError": "timeout"
                if isinstance(error, subprocess.TimeoutExpired)
                else "unavailable",
            }
        if stdout.tell() > 65536 or stderr.tell() > 65536:
            return {"success": False, "transportError": "output_too_large"}
        stdout.seek(0)
        stderr.seek(0)
        return project(completed.returncode, stdout.read(), stderr.read())
