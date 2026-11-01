"""Runs the parity flows on ONE verified PC emulator, against the Flutter app or the native app.

    python3 -I native/e2e/runner/run.py --app flutter --apk R-4600.apk --group G2
    python3 -I native/e2e/runner/run.py --app native --apk app-stable-release.apk --flows E2E-01,E2E-05
    python3 -I native/e2e/runner/run.py --check-syntax          # no device: lint + `maestro check-syntax`
    python3 -I native/e2e/runner/run.py --app flutter --group G2 --dry-run

Device safety (audit codex-04 #14): the serial defaults to emulator-5584 and must look like `emulator-<port>`;
before anything is changed the runner checks that adb lists it, that it is a QEMU guest (ro.kernel.qemu=1) and,
with --expect-avd, that the console reports that AVD name. Every adb and maestro call names that serial, and any
failed adb command stops the run. The owner's phone is never a target. Only the app package under test is ever
cleared, and only through the flows' own `launchApp: clearState`.

Standard library only; run with `python3 -I`. Exit codes: 0 all passed, 1 a flow failed, 2 could not run.
"""
from __future__ import annotations

import argparse
import contextlib
import datetime as dt
import fcntl
import hashlib
import http.server
import json
import os
import pathlib
import re
import secrets as pysecrets
import shutil
import signal
import subprocess
import sys
import threading
import time
import urllib.request

if __package__ in (None, ""):
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
    __package__ = "runner"

from . import catalog, gen_strings, hooks, lint_flows  # noqa: E402

HERE = pathlib.Path(__file__).resolve().parent
E2E = HERE.parent
FIXTURES = E2E / "fixtures"
DEFAULT_SERIAL = "emulator-5584"
DEFAULT_LOCK = os.environ.get("OC_EMULATOR_LOCK", "/home/eslam/Storage/tmp/oc-emulator.lock")
APP_IDS = {
    ("flutter", "stableRelease"): "io.github.eslamasabry.opencode_mobile",
    ("native", "stableRelease"): "io.github.eslamasabry.opencode_mobile",
    ("native", "stableQa"): "io.github.eslamasabry.opencode_mobile",
    ("native", "previewRelease"): "io.github.eslamasabry.opencode_mobile.native",
}
CANARIES = ["sk-e2e-FAKE-0123456789abcdef", "canary-pass-7c1e"]
_SERIAL = re.compile(r"^emulator-\d{4,5}$")


class Refused(Exception):
    """The runner will not touch this device or cannot run safely."""


def scrub(text: str, secrets: list[str]) -> str:
    for s in sorted({*secrets, *CANARIES}, key=len, reverse=True):
        if s:
            text = text.replace(s, "<redacted>")
    return text


class Adb:
    """adb bound to one serial. Calls without '-s' are only `devices`."""

    def __init__(self, serial: str, adb: str = "adb"):
        self.serial, self.adb = serial, adb

    def __call__(self, args: list[str], check: bool = True, timeout: int = 120) -> str:
        if args[:1] != ["devices"] and args[:2] != ["-s", self.serial]:
            args = ["-s", self.serial] + list(args)
        p = subprocess.run([self.adb] + args, capture_output=True, text=True, timeout=timeout)
        if check and p.returncode != 0:
            raise Refused(f"adb {' '.join(args[:6])} failed ({p.returncode})")
        return p.stdout


def verify_device(serial: str, adb, expect_avd: str | None) -> None:
    if not _SERIAL.match(serial or ""):
        raise Refused(f"{serial!r} is not an emulator serial; the runner only drives emulator-<port>")
    listed = adb(["devices"])
    states = dict(l.split("\t", 1) for l in listed.splitlines() if "\t" in l)
    if states.get(serial, "").strip() != "device":
        raise Refused(f"{serial} is not attached (adb devices: {sorted(states)})")
    if adb(["-s", serial, "get-state"]).strip() != "device":
        raise Refused(f"{serial} is not ready")
    qemu = adb(["-s", serial, "shell", "getprop", "ro.kernel.qemu"]).strip() or \
        adb(["-s", serial, "shell", "getprop", "ro.boot.qemu"]).strip()
    if qemu != "1":
        raise Refused(f"{serial} is not an emulator (ro.kernel.qemu != 1)")
    if expect_avd:
        name = adb(["-s", serial, "emu", "avd", "name"]).splitlines()
        if not name or name[0].strip() != expect_avd:
            raise Refused(f"{serial} runs AVD {name[:1]}, expected {expect_avd}")


# ---------------------------------------------------------------- hook server

class HookContext:
    def __init__(self, adb: Adb, app_id: str, args, secrets: list[str]):
        self.adb, self.app_id, self.args, self.secrets = adb, app_id, args, secrets
        self.rooted = False
        self.filled = False
        self.clock_moved = False

    def root(self):
        if not self.rooted:
            self.adb(["root"], timeout=60)
            self.adb(["wait-for-device"], timeout=60)
            self.rooted = True

    def uid_user(self) -> str:
        out = self.adb(["shell", "pm", "list", "packages", "-U", self.app_id])
        m = re.search(r"uid:(\d+)", out)
        if not m:
            raise hooks.HookError("app not installed")
        uid = int(m.group(1))
        return f"u0_a{uid - 10000}"

    def run(self, name: str, args: dict) -> dict:
        hook = hooks.HOOKS.get(name)
        if hook is None:
            raise hooks.HookError(f"unknown hook {name}")
        if hook.root:
            self.root()
        if hook.host:
            return getattr(self, "h_" + hook.host)(args)
        for cmd in hooks.plan(name, args, self.app_id):
            self.adb(cmd)
        if name == "clock-advance":
            self.clock_moved = True
        if name == "locale":
            self.adb(["wait-for-device"], timeout=120)
            _wait_boot(self.adb)
        return {"ok": True}

    def _app_pids(self, match: str) -> list[int]:
        if not re.match(r"^[A-Za-z0-9._-]{1,64}$", match):
            raise hooks.HookError("match not allowed")
        user = self.uid_user()
        out = self.adb(["shell", "ps", "-A", "-o", "PID,USER,ARGS"])
        pids = []
        for line in out.splitlines()[1:]:
            parts = line.split(None, 2)
            if len(parts) == 3 and parts[1] == user and match in parts[2]:
                pids.append(int(parts[0]))
        return pids

    def h_ps_count(self, args):
        return {"count": len(self._app_pids(args.get("match", "")))}

    def h_kill_app_proc(self, args):
        pids = self._app_pids(args.get("match", ""))
        for pid in pids:
            self.adb(["shell", "kill", "-9", str(pid)], check=False)
        return {"killed": len(pids)}

    def h_dumpsys_count(self, args):
        cmd = hooks.plan("dumpsys-count", args, self.app_id)[0]
        match = args.get("match", "")
        if not re.match(r"^[A-Za-z0-9._ -]{0,64}$", match):
            raise hooks.HookError("match not allowed")
        out = self.adb(cmd)
        return {"count": sum(1 for l in out.splitlines() if self.app_id in l and match in l)}

    def h_logcat_scan(self, args):
        out = self.adb(["logcat", "-d"], timeout=120)
        return {"found": any(s and s in out for s in [*CANARIES, *self.secrets])}

    def h_device_http(self, args):
        port = args.get("port")
        path = args.get("path", "/")
        method = args.get("method", "GET")
        if not isinstance(port, int) or not 1024 <= port <= 65535 or method not in ("GET", "POST", "HEAD") \
                or not re.match(r"^/[A-Za-z0-9._~/%-]{0,512}$", path):
            raise hooks.HookError("device-http arguments not allowed")
        local = self.adb(["forward", "tcp:0", f"tcp:{port}"]).strip()
        try:
            req = urllib.request.Request(f"http://127.0.0.1:{local}{path}", method=method,
                                         data=b"" if method == "POST" else None)
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            try:
                with opener.open(req, timeout=20) as r:
                    body = r.read()
                    return {"status": r.status, "disposition": r.headers.get("Content-Disposition", ""),
                            "bytes": len(body)}
            except urllib.error.HTTPError as e:
                return {"status": e.code, "disposition": "", "bytes": 0}
        finally:
            self.adb(["forward", "--remove", f"tcp:{local}"], check=False)

    def h_install_native(self, args):
        apk = self.args.native_apk
        if not apk:
            raise hooks.HookError("run.py was not given --native-apk")
        install_apk(self.adb, apk, self.args.expect_signer)
        return {"installed": True}

    def h_fill_disk(self, args):
        leave = args.get("leave_mb")
        if not isinstance(leave, int) or not 0 <= leave <= 20000:
            raise hooks.HookError("leave_mb out of range")
        out = self.adb(["shell", "df", "-k", "/data"]).splitlines()[-1].split()
        free_kb = int(out[3])
        fill = max(0, free_kb // 1024 - leave)
        self.adb(["shell", "fallocate", "-l", f"{fill}M", "/data/local/tmp/oc-fill.bin"])
        self.filled = True
        return {"filled_mb": fill}

    def h_unfill_disk(self, args):
        self.adb(["shell", "rm", "-f", "/data/local/tmp/oc-fill.bin"], check=False)
        self.filled = False
        return {"ok": True}

    def h_push_media(self, args):
        src = FIXTURES / "device-files"
        if not src.is_dir():
            raise hooks.HookError("native/e2e/fixtures/device-files is missing (S0-FX)")
        for f in sorted(src.iterdir()):
            if f.is_file():
                self.adb(["push", str(f), f"/sdcard/Download/{f.name}"])
                self.adb(["shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                          "-d", f"file:///sdcard/Download/{f.name}"], check=False)
        return {"ok": True}

    def h_camera_scene(self, args):
        # [U] The emulator's virtual scene reads a poster image from the AVD config; the PC recipe sets
        # hw.camera.back=virtualscene and swaps the poster file named by --camera-poster-dir/<image>.png.
        image = args.get("image")
        if image not in ("qr-pairing", "qr-other", "none"):
            raise hooks.HookError("image not allowed")
        poster_dir = pathlib.Path(self.args.camera_poster_dir or FIXTURES / "camera")
        src = poster_dir / f"{image}.png"
        if not src.exists():
            raise hooks.HookError(f"{src} is missing")
        self.adb(["emu", "virtualscene-image", "wall", str(src)], check=False)
        return {"ok": True, "verified": False}

    def h_mic_feed(self, args):
        # [U] Host microphone pass-through (`adb emu avd hostmicon`) plays the WAV through the host's loopback
        # source; the PC recipe routes `paplay <wav>` into it.
        wav = args.get("wav")
        if wav not in ("speech-3min", "phrase-6s", "speech-3min-broken"):
            raise hooks.HookError("wav not allowed")
        src = FIXTURES / "audio" / f"{wav}.wav"
        if not src.exists():
            raise hooks.HookError(f"{src} is missing")
        self.adb(["emu", "avd", "hostmicon"], check=False)
        player = shutil.which("paplay")
        if not player:
            raise hooks.HookError("paplay not installed")
        subprocess.Popen([player, str(src)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return {"ok": True, "verified": False}

    def cleanup(self):
        if self.filled:
            self.h_unfill_disk({})
        if self.clock_moved:
            with contextlib.suppress(Exception):
                self.adb(["shell", "settings", "put", "global", "auto_time", "1"])


def start_hook_server(ctx: HookContext, log: list) -> tuple[http.server.HTTPServer, str]:
    token = pysecrets.token_hex(16)

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *a):  # quiet; the runner keeps its own log
            pass

        def do_POST(self):
            m = re.match(r"^/hook/([A-Za-z0-9-]+)$", self.path)
            if not m or self.headers.get("X-Hook-Token") != token:
                self.send_response(403)
                self.end_headers()
                return
            length = int(self.headers.get("Content-Length", "0") or 0)
            try:
                raw = self.rfile.read(length).decode("utf-8") if length else "{}"
                args = json.loads(raw or "{}")
                result, status = ctx.run(m.group(1), args), 200
            except (hooks.HookError, Refused, ValueError) as e:
                result, status = {"error": str(e)}, 400
            except subprocess.TimeoutExpired:
                result, status = {"error": "timeout"}, 504
            log.append({"hook": m.group(1), "status": status, "at": time.time()})
            body = json.dumps(result).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, token


# ---------------------------------------------------------------- device steps

def _wait_boot(adb, timeout=180):
    end = time.time() + timeout
    while time.time() < end:
        if adb(["shell", "getprop", "sys.boot_completed"], check=False).strip() == "1":
            return
        time.sleep(2)
    raise Refused("device did not finish booting")


def apk_sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def install_apk(adb, apk: str, expect_signer: str | None) -> None:
    if expect_signer:
        signer = shutil.which("apksigner")
        if not signer:
            raise Refused("--expect-signer needs apksigner on PATH")
        out = subprocess.run([signer, "verify", "--print-certs", apk], capture_output=True, text=True).stdout
        digests = {m.upper().replace(":", "") for m in re.findall(r"SHA-256 digest: ([0-9a-fA-F:]+)", out)}
        if expect_signer.upper().replace(":", "") not in digests:
            raise Refused("APK signer does not match --expect-signer")
    adb(["install", "-r", apk], timeout=600)  # never -d, never uninstall


def preflight(args) -> None:
    if args.skip_resource_checks:
        return
    with open("/proc/meminfo") as fh:
        avail = next(int(l.split()[1]) for l in fh if l.startswith("MemAvailable"))
    if avail < 7 * 1024 * 1024:
        raise Refused(f"only {avail // 1024} MB RAM available; 7 GB needed (plan 02 §7.8)")


@contextlib.contextmanager
def emulator_lock(path: str):
    p = pathlib.Path(path)
    if not p.parent.is_dir():
        raise Refused(f"lock folder {p.parent} is missing; pass --lock")
    fh = open(p, "a+")
    try:
        try:
            fcntl.flock(fh, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Refused(f"another run holds {p}")
        yield
    finally:
        fcntl.flock(fh, fcntl.LOCK_UN)
        fh.close()


# ---------------------------------------------------------------- fixtures

def start_fixtures(ids: set[str], out: pathlib.Path, args) -> tuple[dict, list[int]]:
    """Fixture contract (S0-FX): `native/e2e/fixtures/fx.py start --ids A,B --state FILE` starts each fixture,
    writes {"pids": [...], "env": {"SERVER_PASSWORD": ..., "FX_CTL": ...}} to FILE and returns 0.
    `fx.py stop --state FILE` stops them by exact PID. Without fx.py, --fixtures-env FILE supplies the env of
    fixtures someone already started."""
    if args.fixtures_env:
        data = json.loads(pathlib.Path(args.fixtures_env).read_text())
        return data.get("env", data), []
    script = FIXTURES / "fx.py"
    if not script.exists():
        raise Refused("native/e2e/fixtures/fx.py does not exist yet; start the fixtures yourself and pass "
                      "--fixtures-env FILE")
    state = out / "fixtures-state.json"
    subprocess.run([sys.executable, "-I", str(script), "start", "--ids", ",".join(sorted(ids)), "--state",
                    str(state)], check=True, timeout=600)
    data = json.loads(state.read_text())
    return data["env"], [int(p) for p in data.get("pids", [])]


def stop_fixtures(pids: list[int], out: pathlib.Path) -> None:
    script = FIXTURES / "fx.py"
    state = out / "fixtures-state.json"
    if script.exists() and state.exists():
        subprocess.run([sys.executable, "-I", str(script), "stop", "--state", str(state)], timeout=120)
    for pid in pids:  # belt and braces: exact PIDs only, never a pattern
        with contextlib.suppress(ProcessLookupError):
            os.kill(pid, signal.SIGTERM)


# ---------------------------------------------------------------- flows

def select_flows(args) -> list[pathlib.Path]:
    tree = lint_flows.lint_tree(check_keys=False)
    by_id = {fid: catalog.FLOWS_DIR / rel for fid, rel in tree["flows"].items()}
    if args.all:
        ids = sorted(by_id)
    elif args.group:
        ids = catalog.GROUPS[args.group]["flows"]
    else:
        ids = [x.strip() for x in (args.flows or "").split(",") if x.strip()]
    missing = [i for i in ids if i not in by_id]
    if missing:
        raise Refused(f"no flow file for {missing}")
    files = [by_id[i] for i in ids]
    if args.variant == "stableQa":
        files = [f.with_name(f.stem + "-qa.yaml") for f in files if f.with_name(f.stem + "-qa.yaml").exists()]
    return files


def header_of(path: pathlib.Path) -> dict:
    return lint_flows.parse_header(path.read_text(encoding="utf-8").splitlines())


def run_maestro(args, serial, flow: pathlib.Path, outdir: pathlib.Path, env: dict) -> int:
    cmd = [args.maestro, "--device", serial, "test", "--format", "junit", "--output", str(outdir / "report.xml"),
           "--test-output-dir", str(outdir), "--debug-output", str(outdir / "debug"), "--flatten-debug-output"]
    for k, v in env.items():
        cmd += ["-e", f"{k}={v}"]
    cmd.append(str(flow))
    with open(outdir / "maestro.log", "w") as log:
        p = subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT, timeout=args.flow_timeout,
                           env={**os.environ, "MAESTRO_CLI_NO_ANALYTICS": "1"})
    return p.returncode


def scrub_tree(root: pathlib.Path, secrets: list[str]) -> None:
    for p in root.rglob("*"):
        if p.is_file() and p.suffix in (".xml", ".log", ".txt", ".json", ".html"):
            text = p.read_text(encoding="utf-8", errors="replace")
            clean = scrub(text, secrets)
            if clean != text:
                p.write_text(clean, encoding="utf-8")


def check_syntax(args) -> int:
    rep = lint_flows.lint_tree()
    for e in rep["errors"]:
        print("LINT", e)
    bad = len(rep["errors"])
    maestro = shutil.which(args.maestro) or (args.maestro if os.path.exists(args.maestro) else None)
    if not maestro:
        print("maestro not found; lint only")
        return 1 if bad else 0
    for f in lint_flows.flow_files():
        p = subprocess.run([maestro, "check-syntax", str(f)], capture_output=True, text=True,
                           env={**os.environ, "MAESTRO_CLI_NO_ANALYTICS": "1"})
        out = (p.stdout + p.stderr).strip().splitlines()
        result = out[-1] if out else ""
        if p.returncode != 0 or result != "OK":
            bad += 1
            print("SYNTAX", f.relative_to(E2E), " | ".join(l for l in out if "JAVA_TOOL_OPTIONS" not in l))
    print(f"{len(lint_flows.flow_files())} files checked, {bad} problems")
    return 1 if bad else 0


def parse_args(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--app", choices=["flutter", "native"])
    ap.add_argument("--variant", choices=["stableRelease", "stableQa", "previewRelease"], default="stableRelease")
    ap.add_argument("--apk", help="APK under test; installed with `adb install -r` unless already installed")
    ap.add_argument("--native-apk", help="E2E-39: the native APK installed over Flutter by the install-native hook")
    ap.add_argument("--expect-signer", help="SHA-256 of the signing certificate every installed APK must have")
    ap.add_argument("--serial", default=DEFAULT_SERIAL, help="emulator serial (default emulator-5584; never a phone)")
    ap.add_argument("--expect-avd", help="AVD name the serial must run, e.g. OCN_A")
    sel = ap.add_mutually_exclusive_group()
    sel.add_argument("--flows", help="comma list, e.g. E2E-01,E2E-05")
    sel.add_argument("--group", choices=sorted(catalog.GROUPS))
    sel.add_argument("--all", action="store_true")
    ap.add_argument("--lang", choices=["en", "ar"], default="en")
    ap.add_argument("--no-retry", action="store_true")
    ap.add_argument("--no-snapshots", action="store_true", help="do not load the header's AVD snapshot")
    ap.add_argument("--out", type=pathlib.Path)
    ap.add_argument("--maestro", default=os.environ.get("MAESTRO", "maestro"))
    ap.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    ap.add_argument("--lock", default=DEFAULT_LOCK)
    ap.add_argument("--fixtures-env", help="JSON env of already-running fixtures (instead of fixtures/fx.py)")
    ap.add_argument("--camera-poster-dir")
    ap.add_argument("--flow-timeout", type=int, default=3600)
    ap.add_argument("--skip-resource-checks", action="store_true")
    ap.add_argument("--dry-run", action="store_true", help="print what would run; touch no device")
    ap.add_argument("--check-syntax", action="store_true", help="lint + maestro check-syntax every file; no device")
    return ap.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    if args.check_syntax:
        return check_syntax(args)
    if not args.app:
        print("--app is required", file=sys.stderr)
        return 2
    app_id = APP_IDS.get((args.app, args.variant))
    if not app_id:
        print(f"{args.app} has no {args.variant} variant", file=sys.stderr)
        return 2
    try:
        flows = select_flows(args)
    except Refused as e:
        print(f"refused: {e}", file=sys.stderr)
        return 2
    fixtures = set()
    for f in flows:
        fixtures.update(x.strip() for x in header_of(f).get("fixtures", "").split(",") if x.strip())
    fixtures.discard("none")
    if args.dry_run:
        print(json.dumps({"serial": args.serial, "app": args.app, "appId": app_id, "variant": args.variant,
                          "flows": [str(f.relative_to(E2E)) for f in flows],
                          "fixtures": sorted(fixtures),
                          "snapshots": sorted({header_of(f).get("snapshot", "base") for f in flows})}, indent=1))
        return 0
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    out = args.out or HERE / "out" / f"{args.app}-{stamp}"
    out.mkdir(parents=True, exist_ok=True)
    adb = Adb(args.serial, args.adb)
    secrets: list[str] = []
    fixture_pids: list[int] = []
    results = []
    server = ctx = None
    try:
        with emulator_lock(args.lock):
            preflight(args)
            verify_device(args.serial, adb, args.expect_avd)
            gen_strings.write_js(args.lang, HERE / "out/strings.js")
            fx_env, fixture_pids = start_fixtures(fixtures, out, args) if fixtures else ({}, [])
            secrets = [v for k, v in fx_env.items() if "PASSWORD" in k or "TOKEN" in k]
            if args.apk:
                install_apk(adb, args.apk, args.expect_signer)
            ctx = HookContext(adb, app_id, args, secrets)
            hook_log: list = []
            server, token = start_hook_server(ctx, hook_log)
            env = {"APP": args.app, "APP_ID": app_id, "LANG_UNDER_TEST": args.lang,
                   "HOOK_PORT": str(server.server_address[1]), "HOOK_TOKEN": token,
                   "FX_CTL": "http://127.0.0.1:8898", **fx_env}
            secrets.append(token)
            for flow in flows:
                h = header_of(flow)
                fid = h.get("id", flow.stem)
                attempts = []
                for attempt in range(1 if args.no_retry else 2):
                    if h.get("snapshot", "base") != "base" and not args.no_snapshots:
                        adb(["emu", "avd", "snapshot", "load", f"{args.app}-{h['snapshot']}"], timeout=300)
                        _wait_boot(adb)
                        if args.apk:
                            install_apk(adb, args.apk, args.expect_signer)
                    adb(["logcat", "-c"], check=False)
                    fdir = out / fid / f"attempt-{attempt + 1}"
                    fdir.mkdir(parents=True, exist_ok=True)
                    t0 = time.time()
                    rc = run_maestro(args, args.serial, flow, fdir, env)
                    (fdir / "logcat.txt").write_text(scrub(adb(["logcat", "-d"], check=False), secrets))
                    attempts.append({"status": "PASSED" if rc == 0 else "FAILED", "secs": round(time.time() - t0)})
                    if rc == 0:
                        break
                statuses = [a["status"] for a in attempts]
                result = "PASSED" if statuses == ["PASSED"] else ("FLAKY" if "PASSED" in statuses else "FAILED")
                results.append({"flow": fid, "file": str(flow.relative_to(E2E)),
                                "flowSha256": hashlib.sha256(flow.read_bytes()).hexdigest(), "app": args.app,
                                "variant": args.variant, "apkSha256": apk_sha256(args.apk) if args.apk else None,
                                "serial": args.serial, "lang": args.lang, "attempts": attempts, "result": result,
                                "rows": h.get("rows", [])})
                print(f"{fid}: {result}")
    except Refused as e:
        print(f"refused: {e}", file=sys.stderr)
        return 2
    finally:
        if server:
            server.shutdown()
        if ctx:
            ctx.cleanup()
        stop_fixtures(fixture_pids, out)
        (out / "flows.json").write_text(json.dumps(results, indent=1))
        scrub_tree(out, secrets)
    leaked = [p for p in out.rglob("logcat.txt") if "<redacted>" in p.read_text(errors="replace")]
    summary = [f"{r['flow']} {r['result']}" for r in results]
    if leaked:
        summary.append(f"SECRET IN LOGCAT: {[str(p.relative_to(out)) for p in leaked]}")
    (out / "summary.txt").write_text("\n".join(summary) + "\n")
    print("\n".join(summary))
    print(f"outputs: {out}")
    return 1 if leaked or any(r["result"] == "FAILED" for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
