"""Credential-free FQ9 evidence projections over bounded, captured ADB calls.

No device operation occurs until run.py owns the shared emulator flock. Private
data/credentials are transient memory only; raw subprocess/HTTP bodies never
reach reports. This module never starts servers or enrolls providers.
"""

import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

from .common import PACKAGE, SHARED_SERIAL, DriverFailure, digest_file
from .observations import service_foreground, profile_projection
from .runtime import AndroidRuntimeMixin
from .diagnostic_logs import project_log, MAX_BYTES
from .log_severity import project_severity
from .terminal import FAIL_CODES as TERMINAL_CODES

BASE = f"/data/user/0/{PACKAGE}"
FILES = BASE + "/files"
ID_PATTERN = r"[A-Za-z0-9_-]{1,100}"
FGS = PACKAGE + "/.BackgroundConnectionService"
BUILTIN_FGS = PACKAGE + "/.BuiltinServerService"

FAIL_CODES = TERMINAL_CODES | frozenset(
    (
        "command_unavailable_or_timed_out",
        "command_failed",
        "command_output_too_large",
        "android_tool_unavailable",
        "artifact_unavailable",
        "artifact_hash_mismatch",
        "artifact_changed",
        "artifact_package_mismatch",
        "artifact_identity_mismatch",
        "artifact_signer_mismatch",
        "device_unavailable",
        "shared_user_changed",
        "installed_identity_unavailable",
        "installed_apk_path_invalid",
        "installed_apk_unavailable",
        "private_data_unavailable",
        "private_data_too_large",
        "diagnostic_capture_failed",
        "diagnostic_clock_invalid",
        "setup_active_or_unknown",
        "another_live_turn",
        "preservation_fixture_required",
        "preferences_invalid",
        "secure_storage_fixture_required",
        "history_fixture_missing",
        "history_scope_mismatch",
        "history_response_invalid",
        "history_too_large",
        "runtime_password_unavailable",
        "app_managed_socket_unavailable",
        "app_managed_engine_unavailable",
        "forward_failed",
        "protocol_unavailable",
        "protocol_response_invalid",
        "live_receipt_required",
        "live_oc2_observation_unavailable",
        "live_scope_mismatch",
        "live_prompt_mismatch",
        "live_fixture_command_invalid",
        "live_snapshot_invalid",
        "live_fixture_not_unique",
        "live_fixture_not_ready",
        "forward_cleanup_failed",
        "owned_turn_cleanup_failed",
        "sentinel_cleanup_failed",
        "dedicated_avd_required",
        "avd_identity_mismatch",
        "fresh_readback_failed",
        "normal_restore_identity_mismatch",
        "app_launch_failed",
        "fixture_project_unavailable",
        "fixture_seed_invalid",
    )
)


def execute(command, *, timeout=30, limit=2 * 1024 * 1024):
    # Spool output to disk instead of allocating arbitrary dumpsys/pull output
    # on the already memory-constrained host. Never display stderr.
    try:
        with tempfile.TemporaryFile() as output:
            result = subprocess.run(
                command,
                stdout=output,
                stderr=subprocess.DEVNULL,
                timeout=timeout,
                check=False,
            )
            if result.returncode:
                raise DriverFailure("command_failed")
            if output.tell() > limit:
                raise DriverFailure("command_output_too_large")
            output.seek(0)
            return output.read()
    except (OSError, subprocess.TimeoutExpired):
        raise DriverFailure("command_unavailable_or_timed_out") from None


def android_tool(name):
    tool = shutil.which(name)
    if tool:
        return tool
    sdk = Path(
        os.environ.get("ANDROID_SDK_ROOT")
        or os.environ.get("ANDROID_HOME")
        or str(Path.home() / "Android/Sdk")
    )
    candidates = sorted((sdk / "build-tools").glob(f"*/{name}"))
    if not candidates:
        raise DriverFailure("android_tool_unavailable")
    return str(candidates[-1])


def apk_identity(path):
    output = execute([android_tool("aapt"), "dump", "badging", str(path)]).decode(
        "utf-8", errors="replace"
    )
    match = re.search(
        r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",
        output,
        re.M,
    )
    if not match or match[1] != PACKAGE:
        raise DriverFailure("artifact_package_mismatch")
    signed = execute(
        [android_tool("apksigner"), "verify", "--print-certs", str(path)]
    ).decode("utf-8", errors="replace")
    certificates = re.findall(
        r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$", signed, re.M
    )
    if len(certificates) != 1:
        raise DriverFailure("artifact_signer_mismatch")
    return {
        "build": int(match[2]),
        "version": match[3],
        "sha256": digest_file(path),
        "signer": certificates[0].lower(),
    }


def verify_artifact(artifact):
    path = artifact.apk
    if not path.is_file() or path.is_symlink():
        raise DriverFailure("artifact_unavailable")
    before = digest_file(path)
    if before != artifact.sha256:
        raise DriverFailure("artifact_hash_mismatch")
    actual = apk_identity(path)
    if digest_file(path) != before:
        raise DriverFailure("artifact_changed")
    if actual["signer"] != artifact.signer:
        raise DriverFailure("artifact_signer_mismatch")
    if actual != {
        "build": artifact.build,
        "version": artifact.version,
        "sha256": artifact.sha256,
        "signer": artifact.signer,
    }:
        raise DriverFailure("artifact_identity_mismatch")


class AndroidPorts(AndroidRuntimeMixin):
    def __init__(self, serial, run_id, *, history=None, turn=None, dedicated_avd=None):
        self.serial, self.run_id = serial, run_id
        self.history, self.turn = history, turn
        self.dedicated_avd = dedicated_avd
        self.locked = False
        self.mutated = False
        self._forward = None
        self._password = None
        self._uid = None
        self._app_identity = None
        self._socket = None
        self._runtime_version = None
        self._launch_identity = None

    def adb(self, *args, timeout=30, limit=2 * 1024 * 1024):
        if not self.locked:
            raise DriverFailure("device_unavailable")
        return execute(["adb", "-s", self.serial, *args], timeout=timeout, limit=limit)

    def text(self, *args, **kwargs):
        return self.adb(*args, **kwargs).decode("utf-8", errors="replace").strip()

    def device_ready(self):
        if (
            self.text("get-state") != "device"
            or self.text("shell", "getprop", "ro.kernel.qemu") != "1"
        ):
            raise DriverFailure("device_unavailable")
        if self.text("shell", "am", "get-current-user") != "0":
            raise DriverFailure("shared_user_changed")
        if self.serial != SHARED_SERIAL:
            if not self.dedicated_avd:
                raise DriverFailure("dedicated_avd_required")
            names = self.text("emu", "avd", "name").splitlines()
            if names != [self.dedicated_avd, "OK"]:
                raise DriverFailure("avd_identity_mismatch")

    def installed_identity(self):
        listing = self.text(
            "shell", "cmd", "package", "list", "packages", "--user", "0", "-U", PACKAGE
        )
        matches = [
            match
            for row in listing.splitlines()
            if (
                match := re.fullmatch(
                    "package:" + re.escape(PACKAGE) + r" uid:(\d+)", row
                )
            )
        ]
        if len(matches) != 1 or int(matches[0][1]) < 10000:
            raise DriverFailure("installed_identity_unavailable")
        self._uid = int(matches[0][1])
        path = self.text("shell", "pm", "path", "--user", "0", PACKAGE)
        if not re.fullmatch(r"package:/data/app/[^\s\x00]+/base\.apk", path):
            raise DriverFailure("installed_apk_path_invalid")
        with tempfile.TemporaryDirectory(prefix="fq9-apk-") as tmp:
            apk = Path(tmp) / "base.apk"
            self.adb("pull", path.removeprefix("package:"), str(apk), timeout=90)
            if not apk.is_file():
                raise DriverFailure("installed_apk_unavailable")
            identity = apk_identity(apk)
        return {**identity, "uid": self._uid}

    def as_app(self, script, *, limit=2 * 1024 * 1024):
        if self._uid is None:
            raise DriverFailure("installed_identity_unavailable")
        # Approved emulator app-UID mechanism already used by FQ3. No Keystore
        # decryption/credential copying; only the app runtime's own config.
        return self.adb(
            "shell", f"su {self._uid} sh -c {shlex.quote(script)}", limit=limit
        )

    def exists(self, path):
        # Shell cannot traverse private app directories. A denied stat must not
        # be mistaken for clean absence on the dedicated emulator.
        uid = self._uid if self._uid is not None else 0
        script = f"if [ -e {shlex.quote(path)} ]; then echo yes; else echo no; fi"
        result = self.text("shell", f"su {uid} sh -c {shlex.quote(script)}")
        if result not in ("yes", "no"):
            raise DriverFailure("private_data_unavailable")
        return result == "yes"

    def private_bytes(self, path, *, limit=2 * 1024 * 1024):
        if not self.locked or self._uid is None:
            raise DriverFailure("private_data_unavailable")
        # Bound output on the device before capture, and keep private data in
        # memory only. General command spooling must never see these bodies.
        script = f"head -c {limit + 1} {shlex.quote(path)}"
        command = [
            "adb",
            "-s",
            self.serial,
            "exec-out",
            f"su {self._uid} sh -c {shlex.quote(script)}",
        ]
        try:
            result = subprocess.run(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                timeout=15,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            raise DriverFailure("private_data_unavailable") from None
        if result.returncode:
            raise DriverFailure("private_data_unavailable")
        if len(result.stdout) > limit:
            raise DriverFailure("private_data_too_large")
        return result.stdout

    def services(self):
        return self.text("shell", "dumpsys", "activity", "services", PACKAGE)

    def diagnostic_epoch_ms(self):
        raw = self.text("shell", "date", "+%s%3N")
        if not re.fullmatch(r"[0-9]{13}", raw):
            raise DriverFailure("diagnostic_clock_invalid")
        return int(raw)

    def _diagnostic_bytes(self, script):
        # Never use general command spooling for private log bodies. Device
        # output is bounded before capture; stdin is detached from locked PTY.
        if not self.locked or self._uid is None:
            raise DriverFailure("diagnostic_capture_failed")
        try:
            result = subprocess.run(
                ["adb", "-s", self.serial, "exec-out", script],
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                timeout=15,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            raise DriverFailure("diagnostic_capture_failed") from None
        if result.returncode or len(result.stdout) > MAX_BYTES:
            raise DriverFailure("diagnostic_capture_failed")
        return result.stdout

    def diagnostic_logs(self, start, end):
        pid, birth = self._main_process()
        if self._app_identity is not None and (pid, birth) != self._app_identity:
            raise DriverFailure("diagnostic_capture_failed")
        # PID is verified by exact package pidof and birth identity. Capture
        # only its logs, then project their timestamps again within the window.
        script = (
            f"set -o pipefail; logcat -d -v epoch --pid={pid} | tail -c {MAX_BYTES}"
        )
        app = self._diagnostic_bytes("sh -c " + shlex.quote(script))
        path = FILES + "/linux/server.log"
        script = f"tail -c {MAX_BYTES} {shlex.quote(path)}"
        server = self._diagnostic_bytes(f"su {self._uid} sh -c {shlex.quote(script)}")
        # OC1's default logger writes to its XDG data home. server.log may
        # contain only the startup banner; it is not the application log.
        path = FILES + "/linux/ubuntu/root/.local/share/opencode/log/opencode.log"
        script = f"tail -c {MAX_BYTES} {shlex.quote(path)}"
        application = self._diagnostic_bytes(
            f"su {self._uid} sh -c {shlex.quote(script)}"
        )
        if self._main_process() != (pid, birth):
            raise DriverFailure("diagnostic_capture_failed")
        report = {
            "app": project_log(
                app,
                source="app",
                window_start_ms=start,
                window_end_ms=end,
                app_pids={int(pid)},
            ),
            "server": project_log(
                server, source="server", window_start_ms=start, window_end_ms=end
            ),
            "serverApplication": project_log(
                application, source="server", window_start_ms=start, window_end_ms=end
            ),
        }
        # Preserve warning/error counts even for unrecognized messages. Their
        # text remains omitted; an empty category list is not a no-error claim.
        for name, source, raw in (
            ("app", "app", app),
            ("server", "server", server),
            ("serverApplication", "server", application),
        ):
            report[name]["severity"] = project_severity(
                raw, source=source, start_ms=start, end_ms=end
            )
        return report

    def require_idle_setup(self):
        services = self.services()
        if service_foreground(services, PACKAGE + "/.SetupService"):
            raise DriverFailure("setup_active_or_unknown")
        if self.exists(FILES + "/linux/setup.json"):
            try:
                setup = json.loads(self.private_bytes(FILES + "/linux/setup.json"))
                state = setup.get("state")
            except (ValueError, AttributeError):
                raise DriverFailure("setup_active_or_unknown") from None
            if state not in ("done", "cancelled", "interrupted", "failed", "idle"):
                raise DriverFailure("setup_active_or_unknown")
        writer = BASE + "/shared_prefs/builtin_component_writer.xml"
        if self.exists(writer):
            try:
                values = ET.fromstring(self.private_bytes(writer))
                if values.tag != "map" or any(
                    entry.get("name") == "ticket" for entry in values
                ):
                    raise DriverFailure("setup_active_or_unknown")
            except ET.ParseError:
                raise DriverFailure("setup_active_or_unknown") from None

    def install_update(self, artifact):
        self.require_idle_setup()
        verify_artifact(artifact)
        # Preserve full app data and refuse to select/remove the baseline.
        self.mutated = True  # even an interrupted package-manager call may act
        self.adb("install", "-r", "-d", str(artifact.apk), timeout=180)

    def prepare_fixture_project(self):
        """Create only under the app's backing directory for /root/projects.

        The guest mount hides rootfs/root/projects. Writing there on the host
        manufactures a legacy/persistent collision on the next app launch.
        Require the app-prepared backing root; never create a legacy fallback.
        """
        if (
            not self.locked
            or self._uid is None
            or not re.fullmatch(r"fq9-[A-Za-z0-9_-]{1,80}", self.run_id)
        ):
            raise DriverFailure("fixture_project_unavailable")
        projects = FILES + "/projects"
        fixture = projects + "/" + self.run_id
        self.mutated = True  # interrupted mkdir may act; normal restore still runs
        result = self.as_app(
            f"[ ! -L {shlex.quote(projects)} ] && "
            f"[ -d {shlex.quote(projects)} ] && "
            f'[ "$(stat -c %u {shlex.quote(projects)})" = {self._uid} ] || exit 71; '
            f"umask 077; mkdir {shlex.quote(fixture)} || exit 71; "
            "printf fq9_project_created"
        )
        if result != b"fq9_project_created":
            raise DriverFailure("fixture_project_unavailable")
        return "/root/projects/" + self.run_id

    def install_fresh(self, artifact):
        snapshot = self.fresh_snapshot()
        if (
            not snapshot["dedicated"]
            or snapshot["currentUser"] != 0
            or snapshot["packageInstalled"]
            or snapshot["appDataPresent"]
        ):
            raise DriverFailure("dedicated_avd_required")
        verify_artifact(artifact)
        self.mutated = True
        self.adb("install", str(artifact.apk), timeout=180)
        actual = self.installed_identity()
        if any(
            actual[k] != getattr(artifact, k)
            for k in ("build", "version", "sha256", "signer")
        ):
            raise DriverFailure("artifact_identity_mismatch")

    def launch(self):
        self.adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
        for _ in range(10):
            try:
                self._launch_identity = self._main_process()
                return
            except DriverFailure:
                time.sleep(0.2)
        raise DriverFailure("app_launch_failed")

    def app_visible(self):
        activities = self.text("shell", "dumpsys", "activity", "activities")
        return any(
            re.search(
                r"(?:mResumedActivity|topResumedActivity).*"
                + re.escape(PACKAGE)
                + r"/(?:\.|"
                + re.escape(PACKAGE)
                + r"\.)MainActivity\b",
                line,
            )
            for line in activities.splitlines()
        )

    def ui_labels(self):
        path = f"/data/local/tmp/{self.run_id}-ui.xml"
        try:
            self.adb("shell", "uiautomator", "dump", path, timeout=12)
            tree = ET.fromstring(self.adb("exec-out", "cat", path))
            return {
                (node.get("text") or node.get("content-desc") or "")
                .replace("\u2068", "")
                .replace("\u2069", "")
                for node in tree.findall(".//node")
            }
        except ET.ParseError:
            raise DriverFailure("fresh_readback_failed") from None
        finally:
            self.adb("shell", "rm", "-f", path)

    def capture_preservation(self):
        self.require_idle_setup()
        if service_foreground(self.services(), FGS):
            raise DriverFailure("another_live_turn")
        profiles, preferences = profile_projection(
            self.private_bytes(BASE + "/shared_prefs/FlutterSharedPreferences.xml")
        )
        secure_path = BASE + "/shared_prefs/FlutterSecureStorage.xml"
        if not self.exists(secure_path):
            raise DriverFailure("secure_storage_fixture_required")
        secure = hashlib.sha256(self.private_bytes(secure_path)).digest()
        histories = self._history_digests()
        engine = self.history["engine"]
        query = (
            {"location[directory]": self.history["directory"]}
            if engine == "opencode2"
            else {"directory": self.history["directory"]}
        )
        status = self.protocol(
            "GET",
            "/api/session/active" if engine == "opencode2" else "/session/status",
            query=query,
        )
        if engine == "opencode2":
            status = status.get("data") if type(status) is dict else None
            idle = type(status) is dict and not status
        else:
            idle = type(status) is dict and all(
                type(s) is dict and s.get("type") == "idle" for s in status.values()
            )
        if not idle:
            raise DriverFailure("another_live_turn")
        token = os.urandom(32).hex()
        path = FILES + "/" + self.run_id + "-preservation"
        if self.exists(path):
            raise DriverFailure("preservation_fixture_required")
        # Do all other preflights first; do not leave an unreturned handle.
        try:
            self.as_app(
                f"umask 077; set -C; printf %s {shlex.quote(token)} > {shlex.quote(path)}"
            )
            if self.private_bytes(path) != token.encode():
                raise DriverFailure("private_data_unavailable")
        except Exception:
            # A collision/race must never remove someone else's existing file.
            try:
                if self.private_bytes(path) == token.encode():
                    self.as_app("rm -f " + shlex.quote(path))
            except DriverFailure:
                pass
            raise DriverFailure("private_data_unavailable") from None
        self.mutated = True
        return {
            "profiles": profiles,
            "preferences": preferences,
            "secure": secure,
            "histories": histories,
            "path": path,
            "token": token,
        }

    def verify_preservation(self, snapshot):
        # Give the updated app a bounded opportunity to restart its own server;
        # a permanently unavailable runtime remains a failed device check.
        self.close_protocol()
        self._socket = None  # an APK update legitimately restarts its server
        last = None
        deadline = time.monotonic() + 90
        while True:
            try:
                histories = self._history_digests()
                break
            except DriverFailure as failure:
                last = failure
                # An updated runtime may rotate its listener and password while
                # booting. Refresh only here; live dwell identity stays fixed.
                self.close_protocol()
                self._socket = None
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise last
                time.sleep(min(1, remaining))
        profiles, preferences = profile_projection(
            self.private_bytes(BASE + "/shared_prefs/FlutterSharedPreferences.xml")
        )
        secure = hashlib.sha256(
            self.private_bytes(BASE + "/shared_prefs/FlutterSecureStorage.xml")
        ).digest()
        return {
            "sentinel": self.private_bytes(snapshot["path"])
            == snapshot["token"].encode(),
            "profiles": profiles == snapshot["profiles"],
            "preferences": preferences == snapshot["preferences"],
            "secureStorage": secure == snapshot["secure"],
            "histories": histories == snapshot["histories"],
        }

    def remove_sentinel(self, snapshot):
        if (
            snapshot.get("path") != FILES + "/" + self.run_id + "-preservation"
            or self.private_bytes(snapshot["path"]) != snapshot["token"].encode()
        ):
            raise DriverFailure("sentinel_cleanup_failed")
        self.as_app("rm -f " + shlex.quote(snapshot["path"]))
        if self.exists(snapshot["path"]):
            raise DriverFailure("sentinel_cleanup_failed")

    def home(self):
        self.adb("shell", "input", "keyevent", "KEYCODE_HOME")
        # Input delivery can precede Activity pause. Start the measured dwell
        # only after an observed HOME transition, rather than an arbitrary nap.
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            if not self.app_visible():
                return
            time.sleep(0.2)
        raise DriverFailure("background_app_still_visible")

    def monotonic(self):
        return time.monotonic()

    def sleep(self, seconds):
        time.sleep(seconds)

    def resume(self):
        self.launch()
        # Read-only UI evidence, scoped to the dedicated conversation. This
        # does not navigate to an unrelated session or create a foreground turn.
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if self.app_visible() and any(
                self.turn["sessions"][0]["title"] in label for label in self.ui_labels()
            ):
                return
            time.sleep(1)
        raise DriverFailure("app_launch_failed")

    def fresh_snapshot(self):
        self.device_ready()
        packages = self.text(
            "shell", "cmd", "package", "list", "packages", "--user", "0", PACKAGE
        )
        return {
            "dedicated": self.serial != SHARED_SERIAL and bool(self.dedicated_avd),
            "currentUser": 0,
            "packageInstalled": "package:" + PACKAGE in packages,
            "appDataPresent": self.exists(BASE),
        }

    def first_run_snapshot(self):
        deadline = time.monotonic() + 15
        labels = set()
        while time.monotonic() < deadline:
            labels = self.ui_labels()
            if self.app_visible() and any(
                question in label for question in _WELCOME for label in labels
            ):
                break
            time.sleep(1)
        profiles_empty = False
        prefs = BASE + "/shared_prefs/FlutterSharedPreferences.xml"
        if self.exists(prefs):
            try:
                tree = ET.fromstring(self.private_bytes(prefs))
                nodes = [n for n in tree if n.get("name") == "flutter.oc.profiles"]
                profiles_empty = len(nodes) == 0 or (
                    len(nodes) == 1 and json.loads(nodes[0].text or "") == []
                )
            except (ValueError, ET.ParseError):
                raise DriverFailure("fresh_readback_failed") from None
        else:
            profiles_empty = True
        # Being visibly alive at the bounded first-run checkpoint is a narrow
        # crash-free-launch observation, not ANR/lifecycle or future stability.
        return {
            "appVisible": self.app_visible(),
            "onboardingVisible": any(
                question in label for question in _WELCOME for label in labels
            ),
            "profilesEmpty": profiles_empty,
            "setupAbsent": not self.exists(FILES + "/linux/setup.json")
            and not self.exists(FILES + "/linux/ubuntu"),
            "crashFree": self.app_visible()
            and self._launch_identity is not None
            and self._main_process() == self._launch_identity,
        }

    def restore_normal(self, normal):
        # Caller owns lock through final restoration, even on driver failure.
        if not self.mutated:
            return
        self.close_protocol()
        self._socket = None
        actual = self.installed_identity()
        if actual["signer"] != normal.signer:
            raise DriverFailure("normal_restore_identity_mismatch")
        if any(
            actual[k] != getattr(normal, k)
            for k in ("build", "version", "sha256", "signer")
        ):
            self.install_update(normal)
            actual = self.installed_identity()
        if any(
            actual[k] != getattr(normal, k)
            for k in ("build", "version", "sha256", "signer")
        ):
            raise DriverFailure("normal_restore_identity_mismatch")
        self.launch()

    def close_protocol(self):
        self._password = None
        if self._forward is not None:
            port = self._forward
            try:
                self.adb("forward", "--remove", f"tcp:{port}")
            except DriverFailure:
                raise DriverFailure("forward_cleanup_failed") from None
            self._forward = None


def welcome_copy():
    # Read exact shipped English/Arabic source strings offline, without copying
    # arbitrary screen text into artifacts. Root-relative irrespective of cwd.
    root = Path(__file__).resolve().parents[3]
    return tuple(
        json.loads((root / "lib/l10n" / f"app_{locale}.arb").read_text())[
            "firstRunWhereQuestion"
        ]
        for locale in ("en", "ar")
    )


_WELCOME = welcome_copy()
