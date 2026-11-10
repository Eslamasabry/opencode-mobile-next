#!/usr/bin/env python3
"""Short locked BB1 session; outputs only fixture diagnostics and safe outcomes."""
import argparse
import fcntl
import re
import subprocess
from pathlib import Path


SERIAL = "emulator-5554"
PACKAGE = "io.github.eslamasabry.opencode_mobile"
RUNNER = f"{PACKAGE}.test/{PACKAGE}.BuiltinRuntimeAcceptance"
CERT = "1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c"
SAFE_FIELDS = {
    "lastExitCode", "lastUptimeMs", "restartCount", "running", "exitReason",
    "profileDiagnosticsDeleted", "builtinRuntimeResult", "builtinRuntimeFailure",
}


def invoke(args, timeout=40):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout)


def adb(*args):
    return invoke(["adb", "-s", SERIAL, *args])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--runner-apk", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    evidence = []

    def require(value, reason):
        if not value:
            raise RuntimeError(reason)

    def install(path):
        signature = invoke([str(args.apksigner), "verify", "--print-certs", str(path)])
        digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)", signature.stdout)
        require(signature.returncode == 0 and digests == [CERT], "signer_mismatch")
        installed = adb("install", "-r", str(path))
        require(installed.returncode == 0 and "Success" in installed.stdout, "install_refused")
        evidence.append("PASS signed_in_place_install")

    def instrument(step, *extra):
        result = adb("shell", "am", "instrument", "-w", "-e", "step", step, *extra, RUNNER)
        fields = {}
        for line in result.stdout.splitlines():
            match = re.fullmatch(r"INSTRUMENTATION_(?:STATUS|RESULT): ([A-Za-z]+)=([A-Za-z0-9_-]+)", line)
            if match and match[1] in SAFE_FIELDS:
                fields[match[1]] = match[2]
                evidence.append(f"{step} {match[1]}={match[2]}")
        require(result.returncode == 0 and fields.get("builtinRuntimeResult") == "PASS"
                and "INSTRUMENTATION_CODE: -1" in result.stdout, "instrumentation_failed")
        return fields

    try:
        with open("/home/eslam/Storage/tmp/oc-emulator.lock", "a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            require(adb("get-state").stdout.strip() == "device", "emulator_unavailable")
            install(args.apk)
            install(args.runner_apk)
            fields = instrument("diagnostics")
            require(fields.get("profileDiagnosticsDeleted") == "true", "deletion_unproven")
            restarts, uptime = fields.get("restartCount"), fields.get("lastUptimeMs")
            require(restarts and restarts.isdecimal() and uptime and uptime.isdecimal(), "snapshot_missing")
            require(adb("shell", "am", "force-stop", PACKAGE).returncode == 0, "force_stop_failed")
            instrument("persisted", "-e", "expectedRestarts", restarts, "-e", "expectedUptimeMs", uptime)
            evidence.append("PASS BB1_emulator_acceptance")
    except RuntimeError as failure:
        evidence.append(f"FAIL {failure}")
    except (OSError, subprocess.TimeoutExpired):
        evidence.append("FAIL device_command_unavailable")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(evidence) + "\n")
    print("\n".join(evidence))
    return 0 if evidence[-1] == "PASS BB1_emulator_acceptance" else 1


if __name__ == "__main__":
    raise SystemExit(main())
