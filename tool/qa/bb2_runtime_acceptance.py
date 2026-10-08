#!/usr/bin/env python3
"""Short locked BB1+BB2 session; outputs only fixture diagnostics and safe outcomes."""
import argparse
import base64
import copy
import fcntl
import http.client
import io
import json
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path


SERIAL = "emulator-5554"
PACKAGE = "io.github.eslamasabry.opencode_mobile"
RUNNER = f"{PACKAGE}.test/{PACKAGE}.BuiltinRuntimeAcceptance"
CERT = "1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c"
SAFE_FIELDS = {
    "lastExitCode", "lastUptimeMs", "restartCount", "running", "exitReason",
    "profileDiagnosticsDeleted", "builtinRuntimeResult", "builtinRuntimeFailure",
    "nativeAttempts", "restartObservedMs", "actualOpenCodeHealthy", "activityAbsent",
    "restartForegroundRetained", "nativeBudgetExhausted", "policyOffManualReset", "userStopPersisted",
    "qaInitialStopDrained", "trackedExecutableClass",
}


def invoke(args, timeout=40):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout)


def adb(*args):
    return invoke(["adb", "-s", SERIAL, *args])


def connected_open_code_two(values, connected_labels):
    return any(("OpenCode 2" in value or "OpenCode2" in value) and
               any(label in value for label in connected_labels) for value in values)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--runner-apk", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    evidence = []
    native_path = f"/data/user/0/{PACKAGE}/shared_prefs/builtin_server_recovery.xml"
    original_native = None
    changed = False
    qualified = False
    restored = False

    def read_native():
        result = adb("shell", "cat", native_path)
        require(result.returncode == 0 and len(result.stdout) < 1_000_000, "native_snapshot_unavailable")
        return ET.fromstring(result.stdout)

    def attempts(snapshot):
        return sum(json.loads(node.text or "{}").get("attempts", 0) for node in snapshot
                   if node.attrib.get("name", "").startswith("oc.builtinRecoveryBudget.")
                   and not node.attrib["name"].endswith(".qa_bb2_supervision"))

    locale = [json.loads((Path(__file__).resolve().parents[2] / f"lib/l10n/app_{lang}.arb").read_text())
              for lang in ["en", "ar"]]

    def labels(*keys):
        return {value[key] for value in locale for key in keys if key in value}

    def ui():
        path = "/data/local/tmp/oc-bb2-ui-" + uuid.uuid4().hex + ".xml"
        try:
            require(adb("shell", "uiautomator", "dump", path).returncode == 0, "restore_ui_dump_failed")
            result = adb("shell", "cat", path)
            if result.returncode != 0 or "<hierarchy" not in result.stdout:
                return []
            return list(ET.fromstring(result.stdout).iter("node"))
        finally:
            adb("shell", "rm", path)

    def texts(nodes):
        return [node.attrib.get("text", "") + "\n" + node.attrib.get("content-desc", "") for node in nodes]

    def tap(nodes, accepted):
        for node in nodes:
            if not any(line.strip() in accepted for value in
                       [node.attrib.get("text", ""), node.attrib.get("content-desc", "")]
                       for line in value.splitlines()):
                continue
            bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
            if bounds:
                left, top, right, bottom = map(int, bounds.groups())
                require(adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2)).returncode == 0,
                        "restore_ui_tap_failed")
                return True
        return False

    def open_code_two_healthy():
        password = adb("shell", "cat", f"/data/user/0/{PACKAGE}/files/linux/ubuntu/root/.oc-builtin/server.password")
        if password.returncode != 0 or not password.stdout.strip():
            return False
        auth = base64.b64encode(("opencode:" + password.stdout.strip()).encode()).decode()
        class Socket:
            def __init__(self, data): self.data = data
            def makefile(self, *unused): return io.BytesIO(self.data)
        for route in ["/api/health", "/api/info"]:
            request = f"GET {route} HTTP/1.1\r\nHost: 127.0.0.1\r\nAuthorization: Basic {auth}\r\nConnection: close\r\n\r\n".encode()
            result = subprocess.run(["adb", "-s", SERIAL, "shell", "toybox", "nc", "-w", "2", "127.0.0.1", "4097"],
                                    input=request, capture_output=True, timeout=5)
            try:
                response = http.client.HTTPResponse(Socket(result.stdout)); response.begin()
                body = json.loads(response.read())
                if response.status == 200 and isinstance(body, dict) and isinstance(body.get("version"), str) and body.get("healthy") is not False:
                    if route == "/api/health" or isinstance(body.get("pid"), int) or isinstance(body.get("urls"), list):
                        return True
            except (ValueError, OSError, http.client.HTTPException):
                pass
        return False

    def restore_person():
        print("RESTORE native_preferences_and_existing_app", flush=True)
        require(adb("shell", "am", "force-stop", PACKAGE).returncode == 0, "restore_force_stop_failed")
        current = read_native()
        by_name = {node.attrib.get("name"): node for node in current}
        for node in original_native:
            by_name[node.attrib.get("name")] = copy.deepcopy(node)
        # Keep conservatively migrated records created by the app while v2 markers
        # were acknowledged. Restore all original keys; never replenish retries.
        for key in list(by_name):
            if key and key.endswith(".qa_bb2_supervision"):
                del by_name[key]
        for key, value in [("wanted", "false"), ("userStopped", "true")]:
            by_name[key] = ET.Element("boolean", {"name": key, "value": value})
        merged = ET.Element("map")
        for node in by_name.values(): merged.append(node)
        require(adb("shell", "test", "!", "-e", native_path + ".bak").returncode == 0, "restore_native_backup_pending")
        result = subprocess.run(["adb", "-s", SERIAL, "shell", "sh", "-c", f"cat > '{native_path}'"],
                                input=ET.tostring(merged, encoding="unicode"), capture_output=True, text=True, timeout=10)
        require(result.returncode == 0, "restore_native_write_failed")
        evidence.append("PASS original_native_keys_restored_with_temporary_stop")
        require(adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity").returncode == 0, "restore_app_launch_failed")
        time.sleep(3)
        nodes = ui()
        if tap(nodes, labels("serverSwitcherOpen")):
            time.sleep(1)
            nodes = ui()
            tap(nodes, labels("serverSwitcherManage"))
            time.sleep(1)
        deadline = time.monotonic() + 150
        start_pressed = False
        connect_pressed = False
        connected = False
        nodes = []
        while time.monotonic() < deadline:
            nodes = ui(); visible = texts(nodes)
            connected = connected_open_code_two(visible, labels("serverRowConnected", "e7WorkspaceConnected"))
            if connected and open_code_two_healthy(): break
            if not start_pressed:
                start_pressed = tap(nodes, labels("phoneServerStart", "phoneServerStartAndConnect"))
                if start_pressed: evidence.append("PASS explicit_existing_profile_start")
                elif not connect_pressed:
                    connect_pressed = tap(nodes, labels("phoneServerConnect", "phoneSetupStartConnect"))
            time.sleep(1)
        require(connected and open_code_two_healthy(), "restore_opencode2_connection_unproven")
        evidence.append("PASS restored_OpenCode2_authenticated_health_and_connected_UI")
        claude = labels("cardsAgentClaude", "localAgentPageTitle")
        ready = labels("agentsStateReady", "agentsStepReady")
        def claude_ready(values):
            return any(any(name in value for name in claude) and any(state in value for state in ready) for value in values) or any(
                title in value for title in labels("localAgentReadyTitle") for value in values)
        if not claude_ready(texts(nodes)):
            tap(nodes, claude)
            nodes = ui()
        visible = texts(nodes)
        require(claude_ready(visible), "restore_claude_ready_unproven")
        evidence.append("PASS restored_Claude_Code_Ready_UI")
        evidence.append(f"personBudgetAfter={attempts(read_native())}")
        evidence.append("PASS person_runtime_restored")

    def require(value, reason):
        if not value:
            raise RuntimeError(reason)

    def install(path):
        signature = invoke([str(args.apksigner), "verify", "--print-certs", str(path)])
        digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)", signature.stdout)
        require(signature.returncode == 0 and digests == [CERT], "signer_mismatch")
        installed = adb("install", "-r", str(path))
        require("INSTALL_FAILED_VERSION_DOWNGRADE" not in installed.stdout + installed.stderr,
                "install_version_downgrade_refused")
        require(installed.returncode == 0 and "Success" in installed.stdout, "install_refused")
        evidence.append("PASS signed_in_place_install")

    def instrument(step, *extra):
        result = invoke(["adb", "-s", SERIAL, "shell", "am", "instrument", "-w", "-e", "step", step, *extra, RUNNER], timeout=510)
        fields = {}
        series = {}
        for line in result.stdout.splitlines():
            match = re.fullmatch(r"INSTRUMENTATION_(?:STATUS|RESULT): ([A-Za-z]+)=([A-Za-z0-9_-]+)", line)
            if match and match[1] in SAFE_FIELDS:
                fields[match[1]] = match[2]
                series.setdefault(match[1], []).append(match[2])
                evidence.append(f"{step} {match[1]}={match[2]}")
        require(result.returncode == 0 and fields.get("builtinRuntimeResult") == "PASS"
                and "INSTRUMENTATION_CODE: -1" in result.stdout, "instrumentation_failed")
        if step == "supervision":
            require(series.get("nativeAttempts") == ["1", "2", "3"], "native_attempt_series_invalid")
            delays = series.get("restartObservedMs", [])
            require(len(delays) == 3 and all(value.isdecimal() and 1000 <= int(value) <= 60000 for value in delays), "native_backoff_bounds_invalid")
            for field in ["actualOpenCodeHealthy", "activityAbsent", "restartForegroundRetained"]:
                require(series.get(field) == ["true", "true", "true"], f"{field}_unproven")
            require(fields.get("nativeBudgetExhausted") == "true", "native_budget_exhaustion_unproven")
            require(fields.get("policyOffManualReset") == "true", "manual_reset_policy_off_unproven")
        return fields

    try:
        with open("/home/eslam/Storage/tmp/oc-emulator.lock", "a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            require(adb("get-state").stdout.strip() == "device", "emulator_unavailable")
            metadata = invoke([str(args.apksigner.with_name("aapt")), "dump", "badging", str(args.apk)])
            candidate = re.search(r"versionCode='(\d+)'", metadata.stdout)
            installed = adb("shell", "dumpsys", "package", PACKAGE)
            existing = re.search(r"versionCode=(\d+)", installed.stdout)
            require(candidate is not None and existing is not None and int(candidate[1]) >= int(existing[1]),
                    "install_version_downgrade_refused")
            evidence.append(f"installedVersionBefore={existing[1]}")
            evidence.append(f"candidateVersion={candidate[1]}")
            original_native = read_native()
            evidence.append(f"personBudgetBefore={attempts(original_native)}")
            try:
                changed = True
                install(args.apk)
                install(args.runner_apk)
                require(instrument("stopForQa").get("qaInitialStopDrained") == "true", "qa_initial_stop_unproven")
                fields = instrument("diagnostics")
                require(fields.get("profileDiagnosticsDeleted") == "true", "deletion_unproven")
                restarts, uptime = fields.get("restartCount"), fields.get("lastUptimeMs")
                require(restarts and restarts.isdecimal() and uptime and uptime.isdecimal(), "snapshot_missing")
                require(adb("shell", "am", "force-stop", PACKAGE).returncode == 0, "force_stop_failed")
                instrument("persisted", "-e", "expectedRestarts", restarts, "-e", "expectedUptimeMs", uptime)
                evidence.append("PASS BB1_emulator_acceptance")
                instrument("supervision")
                require(adb("shell", "am", "force-stop", PACKAGE).returncode == 0, "force_stop_failed")
                require(instrument("stopPersisted").get("userStopPersisted") == "true", "user_stop_persistence_unproven")
                evidence.append("PASS BB2_emulator_acceptance")
                qualified = True
            except RuntimeError as failure:
                evidence.append(f"FAIL primary_QA_{failure}")
                args.out.parent.mkdir(parents=True, exist_ok=True)
                args.out.write_text("\n".join(evidence) + "\n")
                print(f"PRIMARY_QA {failure}", flush=True)
                raise
            finally:
                if changed:
                    restore_person()
                    restored = True
    except RuntimeError as failure:
        evidence.append(f"FAIL {failure}")
    except (ET.ParseError, ValueError, KeyError, TypeError):
        evidence.append("FAIL native_or_ui_snapshot_invalid")
    except (OSError, subprocess.TimeoutExpired):
        evidence.append("FAIL device_command_unavailable")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(evidence) + "\n")
    print("\n".join(evidence))
    return 0 if qualified and restored else 1


if __name__ == "__main__":
    raise SystemExit(main())
