#!/usr/bin/env python3
"""BB3 phased acceptance. Device execution requires separate explicit EMULATOR GO.

Bootstrap prerequisite (coordinator-owned, under the emulator lock AFTER GO):
install the merged QA target/runner in place, open the existing real in-app
OpenCode2 profile, and choose its explicit healthy Start with restart policy on.
That Start must arm its migrated native budget, recipe and current ownership.
Do not begin by reopening the app automatically or using a synthetic fixture as
the real owner's capability proof. This full harness refuses absent metadata or
a candidate version differing from that preinstalled healthy baseline.

Then run ONE --scenario per session. No nonce, launch input, private response,
account config or credential is retained in host reports. Final restoration
revokes first, restores original safe native metadata/policy digests, then uses
an explicit existing-profile Start to replace stale ownership with fresh proof.
"""
import argparse
import copy
from contextlib import contextmanager
import fcntl
import http.client
import hashlib
import io
import json
import os
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

try:
    from bb2_runtime_acceptance import connected_open_code_two
except ModuleNotFoundError:
    from tool.qa.bb2_runtime_acceptance import connected_open_code_two

SERIAL = "emulator-5554"
PACKAGE = "io.github.eslamasabry.opencode_mobile"
PROFILE = "qa_bb3_reclaim"
RUNNER = f"{PACKAGE}.test/{PACKAGE}.BuiltinRuntimeAcceptance"
CERT = "1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c"
PRIVATE = f"/data/user/0/{PACKAGE}"
NATIVE = f"{PRIVATE}/shared_prefs/builtin_server_recovery.xml"
QA_EVIDENCE = f"{PRIVATE}/files/bb3-runtime-qa.json"
WITNESS_COMMAND = f"{PRIVATE}/files/bb3-witness-command.json"
SENTINEL = f"{PRIVATE}/files/linux/ubuntu/root/.oc-bb3-gate-workload"
SAFE_FIELDS = {
    "builtinRuntimeResult", "builtinRuntimeFailure", "installedCertificateSha256",
    "installedVersion", "baselinePolicyMarkerValid", "baselineMarkerSha256", "baselinePolicySha256",
    "qaInitialStopDrained", "bb3StopRevoked", "bb3StopReason", "bb3CleanupComplete",
    "installedRuntimeQa", "baselineRestorationArmed", "bb3CallbackAppPid",
    "bb3KernelBootIdReadable", "bb3BootCountAvailable", "bb3BootIdentitySucceeded", "bb3BootIdentityFailure",
    "bb3KnownRuntimeSucceeded", "bb3KnownRuntimeFailure", "bb3MemoryRecipePresent", "bb3BoundOwnerEnabledMatches",
    "bb3RestorePhase", "bb3RestoreReason", "bb3WitnessCommandSaved", "bb3StaleWitnessCommitted",
}


class Refused(Exception):
    """Only source-authored safe codes enter this exception."""


def require(value, code):
    if not value:
        raise Refused(code)


def parse_stat(value):
    """Linux comm may contain spaces and parentheses. Start ticks are field 22."""
    end = value.rfind(")")
    require(end > value.find("("), "kernel_identity_invalid")
    fields = value[end + 1:].split()
    require(len(fields) >= 20, "kernel_identity_invalid")
    result = dict(pid=int(value.split(" ", 1)[0]), startTicks=int(fields[19]),
                  parent=int(fields[1]), group=int(fields[2]), session=int(fields[3]))
    require(result["pid"] > 1 and result["startTicks"] > 0, "kernel_identity_invalid")
    result["state"] = fields[0]
    return result


def same_process(expected, actual):
    return bool(actual and actual["state"] not in {"Z", "X"} and
                expected["pid"] == actual["pid"] and expected["startTicks"] == actual["startTicks"])


def parse_status(value):
    """Never carry arbitrary instrumentation/account text into the report."""
    fields = {}
    for line in value.splitlines():
        match = re.fullmatch(r"INSTRUMENTATION_(?:STATUS|RESULT): ([A-Za-z0-9]+)=([A-Za-z0-9_-]+)", line)
        if match and match[1] in SAFE_FIELDS:
            fields[match[1]] = match[2]
    return fields


def instrumentation_detached(value, identity):
    # Validate an AMS process dump, not an empty/failed response. Refuse any active test session.
    require("ACTIVITY MANAGER RUNNING PROCESSES" in value and
            re.search(r"\b" + str(identity["pid"]) + ":" + re.escape(PACKAGE) + "/", value),
            "instrumentation_state_unavailable")
    return "ActiveInstrumentation{" not in value and not re.search(r"(?:mActiveInstrumentation|mInstr)\s*[=:]\s*(?!null\b)\S+", value)


def baseline_policy_valid(native, flutter):
    values, preferences = preference_values(native), preference_values(flutter)
    owner, enabled = values.get("owner"), values.get("enabled")
    if owner is None or owner.tag != "string" or not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", owner.text or "") or owner.text == PROFILE or \
            enabled is None or enabled.tag != "boolean" or enabled.attrib.get("value") != "true":
        return False
    marker = preferences.get(f"flutter.oc.builtinRecovery.{owner.text}")
    policy = preferences.get(f"flutter.oc.automation.{owner.text}")
    try:
        if marker is None or marker.tag != "string":
            return False
        migrated = json.loads(marker.text or "")
        if not isinstance(migrated, dict) or type(migrated.get("version")) is not int or \
                migrated["version"] != 2 or migrated.get("nativeAuthority") is not True:
            return False
        if policy is None:
            return True  # Existing enabled binding plus strict v2 marker supplies the authored default.
        if policy.tag != "string":
            return False
        stored = json.loads(policy.text or "")
        if not isinstance(stored, dict) or type(stored.get("version")) is not int or stored["version"] != 1 or \
                stored.get("supervision") not in {"high", "balanced", "autonomous"}:
            return False
        behaviors = stored.get("behaviors")
        return isinstance(behaviors, dict) and behaviors.get("restartPhoneServer") is True and \
            behaviors.get("pollRestartHealth") is True
    except (ValueError, TypeError):
        return False


def preference_values(root):
    return {node.attrib["name"]: node for node in root if "name" in node.attrib}


def require_installed_candidates(device, candidates):
    for package, apk in candidates:
        require(device.installed_hash(package) == hashlib.sha256(apk.read_bytes()).hexdigest(),
                "installed_candidate_hash_mismatch")


def merge_person_preferences(original, current):
    """Restore owner data while preserving a higher retry count observed during QA."""
    values = preference_values(current)
    for key, node in preference_values(original).items():
        selected = copy.deepcopy(node)
        if key.startswith("oc.builtinRecoveryBudget.") and key in values:
            before = json.loads(node.text or "{}")
            after = json.loads(values[key].text or "{}")
            if after.get("attempts", 0) > before.get("attempts", 0):
                selected = copy.deepcopy(values[key])
        if key == "runtimeGeneration" and key in values:
            selected.attrib["value"] = str(max(int(node.attrib["value"]), int(values[key].attrib["value"])))
        values[key] = selected
    for key in list(values):
        if key.endswith("." + PROFILE) or key in {"restoreOwner", "drainOwner"} and values[key].text == PROFILE:
            del values[key]
    for key, value in [("wanted", "false"), ("userStopped", "true")]:
        values[key] = ET.Element("boolean", {"name": key, "value": value})
    result = ET.Element("map")
    for value in values.values():
        result.append(value)
    return result


def recovery_proven(new_app, foreground, activity_absent, old_drained, healthy, budget, expected_budget):
    # Every condition is necessary. Health alone could belong to the surviving old server.
    return all((new_app, foreground, activity_absent, old_drained, healthy, budget == expected_budget))


def failure_code(failure):
    if isinstance(failure, Refused):
        return str(failure)
    if isinstance(failure, (OSError, subprocess.TimeoutExpired)):
        return "device_command_unavailable"
    return "snapshot_invalid"


@contextmanager
def with_cleanup(session, evidence):
    """Retain the primary failure even when cleanup also fails."""
    primary = None
    try:
        yield session
    except (Refused, ValueError, KeyError, TypeError, ET.ParseError, OSError, subprocess.TimeoutExpired) as failure:
        primary = failure
    try:
        session.cleanup()
    except (Refused, ValueError, KeyError, TypeError, ET.ParseError, OSError, subprocess.TimeoutExpired) as failure:
        evidence.append("FAIL fixture_cleanup_" + failure_code(failure))
        if primary is None:
            primary = failure
    if primary is not None:
        raise primary


class Device:
    def run(self, args, timeout=15, **kwargs):
        return subprocess.run(args, capture_output=True, timeout=timeout, **kwargs)

    def adb(self, *args, timeout=15):
        return self.run(["adb", "-s", SERIAL, *args], timeout=timeout, text=True)

    def cat(self, path, required=True):
        result = self.adb("shell", "cat", path)
        if required:
            require(result.returncode == 0 and len(result.stdout) <= 1_000_000, "private_snapshot_unavailable")
        return result.stdout if result.returncode == 0 else None

    def native(self):
        return ET.fromstring(self.cat(NATIVE))

    def identity(self, pid):
        require(isinstance(pid, int) and pid > 1, "exact_pid_invalid")
        value = self.cat(f"/proc/{pid}/stat", required=False)
        return parse_stat(value) if value else None

    def signal(self, identity, signal):
        require(signal in {"TERM", "KILL"}, "signal_invalid")
        # The device shell rechecks start ticks immediately before signaling.
        # Never kill by name, command pattern, or unqualified PID.
        pid, ticks = identity["pid"], identity["startTicks"]
        require(isinstance(pid, int) and pid > 1 and isinstance(ticks, int) and ticks > 0, "exact_pid_invalid")
        command = (f"s=$(cat /proc/{pid}/stat 2>/dev/null) || exit 0; "
                   "s=${s##*) }; set -- $s; shift 19; "
                   f"[ \"$1\" = '{ticks}' ] || exit 0; kill -{signal} {pid}")
        require(self.adb("shell", "sh", "-c", shlex.quote(command)).returncode == 0, "exact_signal_failed")

    def app_identity(self):
        # A fork can briefly inherit the app's name before exec. Wait for one
        # exact candidate; never choose an arbitrary PID from multiple matches.
        for attempt in range(20):
            result = self.adb("shell", "pidof", PACKAGE, timeout=2)
            pids = result.stdout.split() if result.returncode == 0 else []
            require(all(pid.isdecimal() for pid in pids), "app_identity_ambiguous")
            if len(pids) <= 1:
                return self.identity(int(pids[0])) if pids else None
            if attempt < 19:
                time.sleep(.1)
        raise Refused("app_identity_ambiguous")

    def service_state(self):
        service = self.adb("shell", "dumpsys", "activity", "services", f"{PACKAGE}/.BuiltinServerService")
        require(service.returncode == 0, "service_state_unavailable")
        # Do not retain dumpsys bodies: they can include caller-supplied notification copy.
        return "isForeground=true" in service.stdout and "BuiltinServerService" in service.stdout

    def activity_absent(self):
        result = self.adb("shell", "dumpsys", "activity", "activities")
        require(result.returncode == 0, "activity_state_unavailable")
        return not any(PACKAGE in line and ("ActivityRecord{" in line or "mResumedActivity" in line)
                       for line in result.stdout.splitlines())

    def healthy(self):
        # The password and Basic token never leave the device, appear in argv, or get printed.
        password = f"{PRIVATE}/files/linux/ubuntu/root/.oc-builtin/server.password"
        prefix = (f"[ -s '{password}' ] || exit 1; "
                   f"p=$(cat '{password}') || exit 1; "
                   "a=$(printf 'opencode:%s' \"$p\" | toybox base64 | tr -d '\\r\\n'); unset p; ")
        for route in ["/api/health", "/api/info"]:
            command = prefix + (f"printf 'GET {route} HTTP/1.1\\r\\nHost: 127.0.0.1\\r\\nAuthorization: Basic %s\\r\\nConnection: close\\r\\n\\r\\n' \"$a\" "
                                "| toybox nc -w 1 127.0.0.1 4097 2>/dev/null")
            result = self.adb("shell", "sh", "-c", shlex.quote(command), timeout=4)
            if result.returncode != 0 or len(result.stdout) > 8192:
                continue
            class Socket:
                def makefile(self, *unused):
                    return io.BytesIO(result.stdout.encode())
            try:
                response = http.client.HTTPResponse(Socket()); response.begin()
                body = json.loads(response.read(4097))
                shape = isinstance(body, dict) and isinstance(body.get("version"), str) and bool(body["version"])
                truth = body.get("healthy") is True if route == "/api/health" else (
                    isinstance(body.get("pid"), int) or isinstance(body.get("urls"), list))
                if response.status == 200 and shape and truth:
                    return True
            except (ValueError, OSError, http.client.HTTPException, AttributeError):
                pass
        return False

    def mapped_server(self, identities, executable):
        private_roots = [PRIVATE, "/data/data/" + PACKAGE]
        require(isinstance(executable, str) and
                any(re.fullmatch(re.escape(root) + r"/files/linux/ubuntu/[A-Za-z0-9_./-]+", executable)
                    for root in private_roots) and
                not any(part in {".", ".."} for part in executable.split("/")),
                "server_executable_invalid")
        matches = []
        for identity in identities:
            if not same_process(identity, self.identity(identity["pid"])):
                continue
            maps = self.cat(f"/proc/{identity['pid']}/maps", required=False) or ""
            if any(len(parts := line.split(None, 5)) == 6 and "x" in parts[1] and
                   parts[5].removesuffix(" (deleted)") == executable for line in maps.splitlines()):
                matches.append(identity)
        require(len(matches) == 1, "mapped_server_ambiguous")
        return matches[0]

    def owned_members(self):
        values = preference_values(self.native())
        record = json.loads(values[f"oc.builtinRuntimeOwnership.{PROFILE}"].text)
        root, leader = record["root"], record["leader"]
        require(root is not None and leader is not None and same_process(root, self.identity(root["pid"])), "owned_root_not_current")
        result = self.adb("shell", "ps", "-A", "-o", "PID,PPID")
        require(result.returncode == 0, "process_inventory_unavailable")
        parents = {int(row[0]): int(row[1]) for line in result.stdout.splitlines()
                   if len(row := line.split()) == 2 and all(value.isdecimal() for value in row)}
        pids = {root["pid"]}
        for _ in parents:
            pids.update(pid for pid, parent in parents.items() if parent in pids)
        members = [self.identity(pid) for pid in pids]
        require(all(members), "owned_identity_unavailable")
        require(any(same_process(leader, item) for item in members), "owned_leader_missing")
        return members

    def budget(self):
        values = preference_values(self.native())
        return json.loads(values[f"oc.builtinRecoveryBudget.{PROFILE}"].text)["attempts"]

    def detached(self, identity):
        result = self.adb("shell", "dumpsys", "activity", "processes")
        require(result.returncode == 0 and len(result.stdout) <= 1_000_000, "instrumentation_state_unavailable")
        return instrumentation_detached(result.stdout, identity)

    def wait_detached(self, identity):
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            require(same_process(identity, self.app_identity()), "instrumentation_replaced_app")
            if self.detached(identity):
                return
            time.sleep(.1)
        raise Refused("instrumentation_not_detached")

    def ensure_normal_app(self):
        identity = self.app_identity()
        if identity:
            self.wait_detached(identity)
            return identity
        require(self.adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity").returncode == 0,
                "cleanup_activity_start_failed")
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            identity = self.app_identity()
            if identity:
                self.wait_detached(identity)
                return identity
            time.sleep(.1)
        raise Refused("cleanup_normal_app_unavailable")

    def installed_hash(self, package):
        paths = self.adb("shell", "pm", "path", package)
        require(paths.returncode == 0, "installed_apk_path_unavailable")
        entries = paths.stdout.splitlines()
        require(len(entries) == 1 and re.fullmatch(r"package:/[A-Za-z0-9_./+=~-]+/base\.apk", entries[0]),
                "installed_apk_path_invalid")
        digest = self.adb("shell", "sha256sum", entries[0].removeprefix("package:"))
        require(digest.returncode == 0 and re.match(r"^[a-f0-9]{64}\s", digest.stdout), "installed_apk_hash_unavailable")
        return digest.stdout.split()[0]

    def instrument(self, step, *extra, expected_app=None):
        identity = expected_app or self.app_identity()
        require(identity and same_process(identity, self.app_identity()), "instrumentation_requires_live_normal_app")
        self.wait_detached(identity)
        result = self.adb("shell", "am", "instrument", "--no-restart", "-w", "-e", "step", step,
                          *extra, RUNNER, timeout=90)
        fields = parse_status(result.stdout)
        require(result.returncode == 0 and fields.get("builtinRuntimeResult") == "PASS" and
                "INSTRUMENTATION_CODE: -1" in result.stdout, "instrumentation_failed")
        self.wait_detached(identity)
        return fields


def controlled_stale_witness(device, metadata):
    # Deferred import keeps the helper's kernel parsers shared with this module.
    try:
        from bb3_stale_witness import ControlledStaleWitness
    except ModuleNotFoundError:
        from tool.qa.bb3_stale_witness import ControlledStaleWitness
    return ControlledStaleWitness(device, metadata)


class Session:
    def __init__(self, device, evidence):
        self.device, self.evidence = device, evidence
        self.fixture = None
        self.stale_witness = None

    def prepare(self, stage="armed", stale=False):
        require(self.device.cat(QA_EVIDENCE, required=False) is None, "fixture_evidence_already_present")
        app = self.device.app_identity()
        require(app is not None, "preparation_requires_live_normal_app")
        self.device.instrument("bb3Prepared" if stage == "prepared" else "bb3Prepare", expected_app=app)
        raw = self.device.cat(QA_EVIDENCE)
        value = json.loads(raw)
        # Retain immutable identities for safe cleanup even when later proof fails.
        self.fixture = value
        require(isinstance(value.get("app"), dict), "fixture_app_identity_invalid")
        require(value.get("stage") == stage,
                "fixture_stage_invalid")
        require(value.get("activityAbsent") is True and value.get("members"), "fixture_identity_unproven")
        require(same_process(value["app"], app) and same_process(app, self.device.app_identity()) and
                self.device.activity_absent() and self.device.detached(app), "fixture_app_or_activity_invalid")
        if stage == "armed":
            require(value.get("stickyArmed") is True and value.get("actualOpenCodeHealthy") is True and
                    self.device.service_state() and self.device.healthy(), "initial_canonical_start_unproven")
            self.device.mapped_server(value["members"], value["serverExecutable"])
        if stale:
            require(stage == "armed", "stale_witness_requires_armed_fixture")
            fields = self.device.instrument("bb3WitnessCommand", expected_app=app)
            require(fields.get("bb3WitnessCommandSaved") == "true", "stale_witness_command_unproven")
            self.stale_witness = controlled_stale_witness(self.device, json.loads(self.device.cat(WITNESS_COMMAND)))
            witness = self.stale_witness.start()
            fields = self.device.instrument("bb3StaleWitness", "-e", "witnessPid", str(witness["pid"]),
                                           "-e", "witnessStartTicks", str(witness["startTicks"]), expected_app=app)
            # Keep cleanup identities even when the committed receipt is rejected.
            self.fixture = value = json.loads(self.device.cat(QA_EVIDENCE))
            require(fields.get("bb3StaleWitnessCommitted") == "true" and
                    value.get("staleIdentity") is True and isinstance(value.get("staleWitness"), dict) and
                    all(value["staleWitness"].get(k) == witness.get(k) for k in
                        ("pid", "startTicks", "parent", "group", "session")) and
                    same_process(witness, self.device.identity(witness["pid"])) and
                    same_process(value["app"], app), "stale_witness_receipt_unproven")
            self.evidence.append("PASS independent_app_UID_witness_committed_with_invalid_receipt")
        self.evidence += ["PASS preparation_finished_without_process_replacement", "PASS AMS_instrumentation_detached",
                          "PASS canonical_manual_start" if stage == "armed" else "PASS prepared_gate_held"]

    def kill_app(self, identity):
        require(self.device.activity_absent(), "activity_present_before_app_death")
        require(same_process(identity, self.device.app_identity()), "app_identity_changed_before_death")
        require(self.device.detached(identity), "instrumentation_active_before_app_death")
        self.device.signal(identity, "KILL")
        return time.monotonic()

    def recover(self, old_app, members, expected):
        started = self.kill_app(old_app)
        # Android owns scheduling of whole-process recreation. It is not the
        # native 1-60 s backoff contract. Observe it with its own bounded window.
        os_deadline = started + 120
        app = None
        while time.monotonic() < os_deadline:
            app = self.device.app_identity()
            new_app = bool(app and not same_process(old_app, app))
            require(self.device.activity_absent(), "activity_opened_during_os_recreation")
            if new_app and self.device.service_state():
                break
            time.sleep(.2)
        else:
            raise Refused("os_foreground_recreation_timeout")
        recreated_at = time.monotonic()
        os_delay = int((recreated_at - started) * 1000)
        # Polling, two bounded health endpoints and kernel probes add observer
        # latency. Policy's >=1 s delay is proved by JVM tests, never inferred
        # from a sampled PID. This actual check allows 5 s observation tolerance.
        deadline = recreated_at + 65
        while time.monotonic() < deadline:
            current = self.device.app_identity()
            require(same_process(app, current), "recreated_app_changed_during_native_restore")
            drained = all(not same_process(old, self.device.identity(old["pid"])) for old in members)
            require(self.device.activity_absent(), "activity_opened_during_os_recovery")
            if recovery_proven(True, self.device.service_state(), True, drained,
                               self.device.healthy(), self.device.budget(), expected):
                elapsed = int((time.monotonic() - started) * 1000)
                restored_elapsed = int((time.monotonic() - recreated_at) * 1000)
                require(restored_elapsed <= 65000, "native_restore_observation_out_of_bounds")
                replacement = self.device.owned_members()
                require(self.device.detached(app), "instrumentation_active_after_os_recreation")
                self.device.mapped_server(replacement, self.fixture["serverExecutable"])
                self.evidence += [f"nativeAttempts={expected}", f"restartObservedMs={elapsed}",
                                  f"osRecreationObservedMs={os_delay}", f"nativeRestoreObservedMs={restored_elapsed}",
                                  "nativeObserverToleranceMs=5000",
                                  "PASS OS_recreated_FGS_without_Activity", "PASS exact_old_server_tree_drained",
                                  "PASS real_authenticated_OpenCode2_replacement"]
                return app, replacement
            time.sleep(.2)
        raise Refused("os_recovery_timeout")

    def no_resurrection(self, members, expected_budget, seconds=65):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            require(self.device.activity_absent(), "activity_opened_during_revocation")
            require(self.device.budget() == expected_budget, "revocation_spent_budget")
            require(not self.device.healthy(), "revoked_server_resurrected")
            time.sleep(.5)
        require(all(not same_process(old, self.device.identity(old["pid"])) for old in members), "revoked_tree_not_drained")
        require(not self.device.service_state(), "revoked_foreground_retained")
        self.evidence.append("PASS persisted_revocation_no_resurrection")

    def cleanup(self):
        if self.stale_witness is not None:
            self.stale_witness.cleanup()
            self.stale_witness = None
            self.evidence.append("PASS exact_independent_witness_tree_drained")
        # Failure/stale/prepared leftovers: signal only captured QA identities.
        if self.fixture is None:
            raw = self.device.cat(QA_EVIDENCE, required=False)
            if raw:
                self.fixture = json.loads(raw)
        if self.fixture:
            if self.fixture.get("stage") == "prepared" and same_process(self.fixture["app"], self.device.app_identity()):
                self.device.signal(self.fixture["app"], "KILL")  # Ends the held worker without permit dispatch.
            members = self.fixture["members"] + self.fixture.get("fixtureOthers", [])
            for signal in ["TERM", "KILL"]:
                for identity in reversed(members):
                    if same_process(identity, self.device.identity(identity["pid"])):
                        self.device.signal(identity, signal)
                time.sleep(.2)
            require(all(not same_process(item, self.device.identity(item["pid"])) for item in members),
                    "fixture_cleanup_tree_not_drained")
        self.device.ensure_normal_app()  # Cleanup only: never used to trigger an observed OS restoration.
        require(self.device.instrument("bb3Cleanup").get("bb3CleanupComplete") == "true", "fixture_cleanup_unproven")
        self.fixture = None


def wait_for_app_death(device):
    """Observe absence only; never choose or adopt any returned process identity."""
    deadline = time.monotonic() + 10
    while True:
        remaining = deadline - time.monotonic()
        require(remaining > 0, "restore_app_death_timeout")
        try:
            result = device.adb("shell", "pidof", PACKAGE, timeout=min(2, remaining))
        except subprocess.TimeoutExpired:
            continue
        require(len(result.stdout) <= 512 and len(result.stderr) <= 512,
                "restore_app_state_unavailable")
        pids = result.stdout.split()
        require(all(pid.isdecimal() and int(pid) > 1 for pid in pids) and
                ((result.returncode == 0 and pids) or
                 (result.returncode == 1 and not pids and not result.stderr.strip())),
                "restore_app_state_unavailable")
        if not pids:
            return
        remaining = deadline - time.monotonic()
        require(remaining > 0, "restore_app_death_timeout")
        time.sleep(min(.1, remaining))


def restore_person(device, original, evidence, baseline=None):
    require(device.adb("shell", "am", "force-stop", PACKAGE).returncode == 0, "restore_force_stop_failed")
    wait_for_app_death(device)
    require(device.app_identity() is None, "restore_metadata_writer_active")
    merged = merge_person_preferences(original, device.native())
    require(device.adb("shell", "test", "!", "-e", NATIVE + ".bak").returncode == 0, "restore_backup_pending")
    require(device.app_identity() is None, "restore_metadata_writer_active")
    result = device.run(["adb", "-s", SERIAL, "shell", "sh", "-c", shlex.quote(f"cat > '{NATIVE}'")],
                        input=ET.tostring(merged, encoding="unicode"), text=True, timeout=5)
    require(result.returncode == 0, "restore_preferences_failed")
    require(device.app_identity() is None, "restore_metadata_writer_active")
    evidence.append("PASS original_native_preferences_preserved")
    device.ensure_normal_app()
    if baseline is not None:
        restored = device.instrument("bb3Preflight")
        require(all(restored.get(key) == baseline.get(key) for key in
                    ["baselineMarkerSha256", "baselinePolicySha256"]) and
                restored.get("baselinePolicyMarkerValid") == "true", "person_policy_or_marker_changed")
        evidence.append("PASS existing_owner_policy_and_marker_digests_preserved")
    require(device.adb("shell", "am", "start", "-n", f"{PACKAGE}/.MainActivity").returncode == 0, "restore_activity_failed")
    locale = [json.loads((Path(__file__).resolve().parents[2] / f"lib/l10n/app_{lang}.arb").read_text()) for lang in ["en", "ar"]]
    def labels(*keys):
        return {values[key] for values in locale for key in keys if key in values}
    ui_path = "/data/local/tmp/oc-bb3-restore.xml"
    def nodes():
        try:
            require(device.adb("shell", "uiautomator", "dump", ui_path).returncode == 0, "restore_ui_unavailable")
            raw = device.cat(ui_path)
            return list(ET.fromstring(raw).iter("node"))
        finally:
            device.adb("shell", "rm", "-f", ui_path)
    def tap(items, accepted):
        for node in items:
            if not any(line.strip() in accepted for field in ["text", "content-desc"]
                       for line in node.attrib.get(field, "").splitlines()):
                continue
            bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
            if bounds:
                left, top, right, bottom = map(int, bounds.groups())
                require(device.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2)).returncode == 0, "restore_tap_failed")
                return True
        return False
    time.sleep(2)
    if tap(nodes(), labels("serverSwitcherOpen")):
        time.sleep(1); tap(nodes(), labels("serverSwitcherManage")); time.sleep(1)
    deadline = time.monotonic() + 150
    start_pressed = False
    while time.monotonic() < deadline:
        items = nodes()
        visible = [node.attrib.get("text", "") + "\n" + node.attrib.get("content-desc", "") for node in items]
        if start_pressed and connected_open_code_two(visible, labels("serverRowConnected", "e7WorkspaceConnected")) and device.healthy():
            original_values = preference_values(original)
            restored_values = preference_values(device.native())
            owner = original_values["owner"].text
            require(restored_values.get("restoreOwner") is not None and restored_values["restoreOwner"].text == owner,
                    "person_start_did_not_rearm_recipe")
            key = f"oc.builtinRuntimeOwnership.{owner}"
            previous = json.loads(original_values[key].text)
            current = json.loads(restored_values[key].text)
            require(current["generation"] > previous["generation"] and
                    same_process(current["root"], device.identity(current["root"]["pid"])) and
                    same_process(current["leader"], device.identity(current["leader"]["pid"])),
                    "person_start_current_ownership_unproven")
            evidence.append("PASS explicit_person_Start_rearmed_fresh_kernel_ownership")
            evidence.append("PASS shared_owner_authenticated_OpenCode2_and_connected_UI_restored")
            return
        if not start_pressed:
            start_pressed = tap(items, labels("phoneServerStart", "phoneServerStartAndConnect"))
            if not start_pressed:
                tap(items, labels("phoneServerConnect", "phoneSetupStartConnect"))
        time.sleep(1)
    raise Refused("shared_owner_runnable_restore_unproven")



@contextmanager
def emulator_session_lock(inherited_fd=None, path="/home/eslam/Storage/tmp/oc-emulator.lock"):
    # A batch bootstrap may inherit flock's open-file description. Lock that
    # same description so installation, observation and restore remain atomic
    # with respect to other lanes, without deadlocking on a second open.
    with open(path, "a") as lock:
        if inherited_fd is None:
            fcntl.flock(lock, fcntl.LOCK_EX)
        else:
            require(type(inherited_fd) is int and inherited_fd >= 3, "inherited_lock_invalid")
            expected, actual = os.fstat(lock.fileno()), os.fstat(inherited_fd)
            require((actual.st_dev, actual.st_ino) == (expected.st_dev, expected.st_ino), "inherited_lock_wrong_file")
            fcntl.flock(inherited_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emulator-go", action="store_true", required=True,
                        help="Only supply after coordinator's separate EMULATOR GO")
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--runner-apk", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--scenario", choices=["recovery", "stop", "timeout", "prepared", "stale"], required=True,
                        help="One bounded device session per invocation")
    parser.add_argument("--inherited-emulator-lock-fd", type=int, help="Private batch bootstrap: inherited flock descriptor")
    args = parser.parse_args()
    evidence, primary, restore_failure = [], None, None
    device = Device()
    original = None
    preflight = None
    changed = False
    try:
        with emulator_session_lock(args.inherited_emulator_lock_fd):
            require(device.adb("get-state").stdout.strip() == "device", "emulator_unavailable")
            for apk in [args.apk, args.runner_apk]:
                signature = device.run([str(args.apksigner), "verify", "--print-certs", str(apk)], text=True)
                digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)", signature.stdout)
                require(signature.returncode == 0 and digests == [CERT], "candidate_signer_mismatch")
            metadata = device.run([str(args.apksigner.with_name("aapt")), "dump", "badging", str(args.apk)], text=True)
            candidate = re.search(r"versionCode='(\d+)'", metadata.stdout)
            installed = re.search(r"versionCode=(\d+)", device.adb("shell", "dumpsys", "package", PACKAGE).stdout)
            require(candidate and installed and int(candidate[1]) >= int(installed[1]), "version_downgrade_refused")
            require(candidate[1] == installed[1], "preinstall_candidate_and_explicit_real_owner_start_required")
            require_installed_candidates(device, [(PACKAGE, args.apk), (PACKAGE + ".test", args.runner_apk)])
            require(device.app_identity() is not None, "real_owner_normal_app_required")
            original = device.native()
            values = preference_values(original)
            owner = values.get("restoreOwner")
            require(owner is not None and owner.text and owner.text != PROFILE and
                    f"oc.builtinRuntimeRecipe.{owner.text}" in values and
                    f"oc.builtinRuntimeOwnership.{owner.text}" in values and
                    values.get("wanted") is not None and values["wanted"].attrib.get("value") == "true" and
                    values.get("enabled") is not None and values["enabled"].attrib.get("value") == "true",
                    "real_owner_canonical_manual_start_required")
            require(values.get("owner") is not None and values["owner"].text == owner.text and
                    baseline_policy_valid(original, ET.fromstring(device.cat(f"{PRIVATE}/shared_prefs/FlutterSharedPreferences.xml"))),
                    "baseline_policy_or_marker_unproven")
            require(device.healthy() and device.service_state(), "baseline_authenticated_OpenCode2_unavailable")
            try:
                changed = True
                # Installation is a coordinator bootstrap prerequisite; never replace an armed baseline.
                preflight = device.instrument("bb3Preflight")
                require(preflight.get("installedCertificateSha256") == CERT and
                        preflight.get("installedVersion") == candidate[1] and
                        preflight.get("installedRuntimeQa") == "true" and
                        preflight.get("baselineRestorationArmed") == "true", "installed_signer_or_version_mismatch")
                require(preflight.get("baselinePolicyMarkerValid") == "true", "baseline_policy_or_marker_unproven")
                evidence += ["PASS installed_signer_and_version_preflight", f"installedVersion={candidate[1]}"]
                require(device.instrument("stopForQa").get("qaInitialStopDrained") == "true", "initial_stop_not_drained")
                for scenario in [args.scenario]:
                    session = Session(device, evidence)
                    with with_cleanup(session, evidence):
                        session.prepare("prepared" if scenario == "prepared" else "armed", stale=scenario == "stale")
                        initial = session.fixture
                        if scenario == "recovery":
                            app, members = initial["app"], initial["members"]
                            for attempt in range(1, 4):
                                app, members = session.recover(app, members, attempt)
                            # Fourth actual app death cannot spend a fourth retry.
                            session.kill_app(app)
                            session.no_resurrection(members, 3)
                            evidence.append("PASS shared_native_three_attempt_budget_exhausted")
                        elif scenario in {"stop", "timeout"}:
                            restored_app, restored_members = session.recover(initial["app"], initial["members"], 1)
                            require(device.service_state(), "restored_live_service_missing")
                            fields = device.instrument("bb3Stop" if scenario == "stop" else "bb3Timeout", expected_app=restored_app)
                            require(fields.get("bb3CallbackAppPid") == str(restored_app["pid"]) and
                                    fields.get("bb3StopRevoked") == "true" and fields.get("bb3StopReason") ==
                                    ("stopped" if scenario == "stop" else "systemTimeout"), "stop_or_timeout_callback_unproven")
                            app = device.app_identity()
                            if app:
                                session.kill_app(app)
                            session.no_resurrection(restored_members, 1)
                            evidence.append("PASS notification_Stop_persisted" if scenario == "stop" else "PASS real_live_Service_onTimeout_callback_persisted")
                        elif scenario == "prepared":
                            session.kill_app(initial["app"])
                            deadline = time.monotonic() + 5
                            while time.monotonic() < deadline:
                                require(device.adb("shell", "test", "!", "-e", SENTINEL).returncode == 0, "prepared_gate_executed_workload")
                                require(not device.healthy(), "prepared_gate_started_server")
                                time.sleep(.2)
                            evidence.append("PASS prepared_app_death_never_executed_workload")
                        elif scenario == "stale":
                            old = initial["staleWitness"]
                            session.kill_app(initial["app"])
                            deadline = time.monotonic() + 65
                            recreated = False
                            while time.monotonic() < deadline:
                                app = device.app_identity()
                                recreated |= bool(app and not same_process(initial["app"], app))
                                require(device.activity_absent(), "stale_recovery_opened_activity")
                                if not same_process(old, device.identity(old["pid"])):
                                    evidence.append("staleWitnessExitedBeforeNewApp=" + str(not recreated).lower())
                                    evidence.append("staleWitnessExitNativeAttempts=" + str(device.budget()))
                                    raise Refused("stale_witness_lifetime_unproven")
                                require(device.budget() == 0, "stale_identity_spent_budget")
                                time.sleep(.5)
                            require(recreated and not device.service_state(), "stale_restore_refusal_unproven")
                            evidence.append("PASS stale_identity_refused_without_signal_or_retry")
                        evidence.append(f"PASS scenario_{scenario}")
            except (Refused, ValueError, KeyError, TypeError, ET.ParseError, OSError, subprocess.TimeoutExpired) as failure:
                primary = failure_code(failure)
                evidence.append(f"FAIL primary_{primary}")
            finally:
                if changed:
                    try:
                        restore_person(device, original, evidence, preflight)
                    except (Refused, ValueError, KeyError, TypeError, ET.ParseError, OSError, subprocess.TimeoutExpired) as failure:
                        restore_failure = failure_code(failure)
                        evidence.append(f"FAIL restoration_{restore_failure}")
    except Refused as failure:
        primary = str(failure)
        evidence.append(f"FAIL {primary}")
    except (ValueError, KeyError, TypeError, ET.ParseError):
        primary = "snapshot_invalid"
        evidence.append("FAIL snapshot_invalid")
    except (OSError, subprocess.TimeoutExpired):
        primary = "device_command_unavailable"
        evidence.append("FAIL device_command_unavailable")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(evidence) + "\n")
    print("\n".join(evidence))
    return 0 if changed and primary is None and restore_failure is None else 1


if __name__ == "__main__":
    raise SystemExit(main())
