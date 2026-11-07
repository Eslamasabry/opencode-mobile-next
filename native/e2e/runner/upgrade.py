"""The upgrade gate (E00-S09): the released Flutter build, seeded, then the native build installed over it with
`adb install -r`, then every Part C assertion, on one fresh emulator. Driven by native/tool/qa/upgrade_e2e.sh.

    python3 -I native/e2e/runner/upgrade.py --flutter-apk R.apk --native-apk N.apk --expect-signer <sha> --avd OCN_A

Steps (each prints one PASS / FAIL line):
  1. Refuse unless both APKs are signed by --expect-signer only, carry the same package, and the native versionCode is
     above the Flutter one (aapt2 + apksigner from PATH or $ANDROID_HOME/build-tools).
  2. Take the emulator lock, start AVD --avd on port 5584 (emulator-5584): from snapshot flutter-2.1.4-seeded when
     the AVD has it (Part A already done; its fixture state lives next to it under --seed-home), else wiped, so Part A
     seeds the Flutter app from nothing and the snapshot is saved for the next run.
  3. Start the E2E-39 fixtures and FX-CTL (fixtures/fx.py, state kept under --seed-home so the snapshot's saved passwords keep
     working), then `run.py --app native --flows E2E-39`: Part A (unless restored), Part B on Flutter, the
     install-native hook (saves the device state to before.json, then `adb install -r`), the first native launch,
     the upgrade-capture hook (after-migration.json), Part C on native and the native-only checks.
  4. Judge: UI assertions from Maestro's per-command status, OS-held state from before.json vs after-migration.json.
  5. Write docs/qa/native-switch-<date>/README.md with versions, signers, a verdict per assertion and one screenshot
     per screen. Exit 0 when every assertion passed, 1 when one failed, 2 when the run could not happen.

Never touches anything but emulator-5584; never uninstalls; never prints a password. Standard library only.
"""
from __future__ import annotations

import argparse
import contextlib
import dataclasses
import datetime as dt
import fcntl
import glob
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import time

if __package__ in (None, ""):
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
    __package__ = "runner"

from . import device_state, run  # noqa: E402

HERE = pathlib.Path(__file__).resolve().parent
E2E = HERE.parent
REPO = E2E.parent.parent
FX = E2E / "fixtures" / "fx.py"
APP_ID = "io.github.eslamasabry.opencode_mobile"
PORT = 5584
SERIAL = f"emulator-{PORT}"
DEFAULT_SNAPSHOT = "flutter-2.1.4-seeded"
FLOW = "E2E-39"
# E2E-39's header fixtures that have a server script; fx-proxy is the fixtures' own front proxy.
FIXTURES = ("FX-OC2", "FX-OC2-B", "FX-TLS", "FX-LINUXDL", "FX-GH", "fx-proxy")
# What run.py and the flows read outside native/: gen_strings.py (strings.js) and the flow catalog. A sparse checkout
# that holds only native/ (and docs/) cannot run the gate.
NEEDED_FILES = ("lib/l10n/app_en.arb", "lib/l10n/app_ar.arb", "android/app/src/main/res/values/strings.xml",
                "docs/design/native-rewrite-2026-11-01/catalog/e2e-flows.md")
RAM_NEEDED_KB = 7 * 1024 * 1024       # plan 02 §7.8, checked before the gate boots its own AVD
FLOW_TIMEOUT_S = 3 * 3600
SNAPSHOT_FREE_BYTES = 10 * 1024 ** 3  # a snapshot save on a full disk pauses the emulator for good
OWNED_MARK = ".oc-upgrade-e2e"

# Part C, as the UI proves it: (id, what the owner reads, Maestro labels that must all have COMPLETED).
UI_ASSERTIONS = (
    ("part-b", "Part C passed on the Flutter app first (the baseline)", ("S2",)),
    ("first-launch", "A first launch killed mid-migration resumes", ("S10",)),
    ("conversations", "Conversations shown, no welcome screen and no first-run guide", ("S4",)),
    ("settings", "Language, text size, style and notification choices kept", ("S5",)),
    ("servers", "Servers listed and connect without re-typing passwords", ("S6",)),
    ("chat-messages", "A seeded chat opens and shows its messages", ("UC-chat-messages",)),
    ("drafts", "Draft, attachment, saved prompt, pins and always-allow rule kept; queued prompt sends once",
     ("S7",)),
    ("send-prompt", "A new prompt sends and its reply arrives", ("UC-send-prompt",)),
    ("linux-ui", "Built-in Linux, the project and its server there without reinstall", ("S8",)),
    ("terminal", "`pwd && ls` runs in the seeded project's terminal", ("UC-terminal",)),
    ("voice-ready", "Voice pack shows Ready", ("UC-voice-ready",)),
    ("schedule", "Scheduled prompt listed and still fires", ("UC-schedule-listed", "S12")),
    ("old-files", "Attachments from the old documents folder open", ("S9",)),
    ("no-secret-in-log", "No password or token in the log during the update", ("S11",)),
)
# Screens Part C photographs (takeScreenshot "native-<name>"), with the sentence the evidence record shows.
SCREENS = {
    "conversations": "The Conversations list right after the update, no welcome screen.",
    "servers": "Servers: every saved server connected, no password asked.",
    "chat": "The seeded conversation with its earlier messages.",
    "send": "A prompt sent after the update and its reply.",
    "terminal": "The project's terminal after `pwd && ls`.",
    "voice": "Voice typing set up and ready, nothing downloaded again.",
    "schedule": "Scheduled prompts: the seeded schedule is still listed.",
    "linux": "This phone: built-in Linux and its server running.",
    "appearance": "Appearance: the style and text size chosen on 2.1.4.",
    "saved-prompts": "Saved prompts: the prompt saved for later on 2.1.4.",
}


class Refused(Exception):
    """The gate will not run (wrong APKs, busy emulator, missing tool)."""


@dataclasses.dataclass
class Line:
    id: str
    title: str
    ok: bool
    detail: str = ""
    not_run: bool = False  # B-153: nothing to judge (the seed could not be made); never counted as a pass

    @property
    def word(self) -> str:
        return "NOT RUN" if self.not_run else "PASS" if self.ok else "FAIL"

    @property
    def failed(self) -> bool:
        return not self.ok and not self.not_run

    def text(self) -> str:
        return f"{self.word} {self.id}: {self.title}" + (f" ({self.detail})" if self.detail else "")


def say(line: Line) -> Line:
    print(line.text(), flush=True)
    return line


# ---------------------------------------------------------------- step 1: the two APKs

def find_tool(name: str, env=os.environ) -> str | None:
    found = shutil.which(name)
    if found:
        return found
    for home in (env.get("ANDROID_HOME"), env.get("ANDROID_SDK_ROOT")):
        if home:
            hits = sorted(glob.glob(os.path.join(home, "build-tools", "*", name)))
            if hits:
                return hits[-1]
    return None


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def norm_digest(value: str) -> str:
    return value.upper().replace(":", "").strip()


def parse_badging(text: str) -> dict:
    m = re.search(r"^package: name='([^']+)' versionCode='(\d+)'(?: versionName='([^']*)')?", text, re.M)
    if not m:
        raise Refused("aapt2 could not read the package line")
    return {"package": m.group(1), "versionCode": int(m.group(2)), "versionName": m.group(3) or ""}


def parse_signers(text: str) -> list[str]:
    return sorted({norm_digest(d) for d in re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F:]+)", text)})


def apk_facts(apk: str, aapt2: str, apksigner: str, runner=subprocess.run) -> dict:
    if not os.path.isfile(apk):
        raise Refused(f"{apk} does not exist")
    badging = runner([aapt2, "dump", "badging", apk], capture_output=True, text=True)
    if badging.returncode != 0:
        raise Refused(f"aapt2 could not read {os.path.basename(apk)}")
    facts = parse_badging(badging.stdout)
    verify = runner([apksigner, "verify", "--print-certs", apk], capture_output=True, text=True)
    facts["signatureValid"] = verify.returncode == 0
    facts["signers"] = parse_signers(verify.stdout) if verify.returncode == 0 else []
    facts["file"] = os.path.basename(apk)
    facts["sha256"] = sha256_file(apk)
    return facts


def apk_gates(flutter: dict, native: dict, expect_signer: str) -> list[Line]:
    want = norm_digest(expect_signer)
    lines = []
    for name, f in (("flutter", flutter), ("native", native)):
        ok = f["signatureValid"] and f["signers"] == [want]
        detail = f"{f['file']} signed by {', '.join(s[:16] + '...' for s in f['signers']) or 'nothing valid'}"
        lines.append(Line(f"signer-{name}", f"The {name} APK is signed by the expected key only", ok, detail))
    lines.append(Line("same-signer", "Both APKs carry the same signer",
                      bool(flutter["signers"]) and flutter["signers"] == native["signers"]))
    lines.append(Line("same-package", f"Both APKs are {APP_ID}",
                      flutter["package"] == native["package"] == APP_ID,
                      f"{flutter['package']} / {native['package']}"))
    lines.append(Line("version-order", "The native versionCode is above the Flutter one",
                      native["versionCode"] > flutter["versionCode"],
                      f"{flutter['versionCode']} -> {native['versionCode']}"))
    return lines


# ---------------------------------------------------------------- step 2: lock and emulator

@contextlib.contextmanager
def hold_lock(path: str):
    p = pathlib.Path(path)
    if not p.parent.is_dir():
        raise Refused(f"lock folder {p.parent} is missing; pass --lock or set OC_EMULATOR_LOCK")
    fh = open(p, "a+")
    try:
        try:
            fcntl.flock(fh, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Refused(f"another run holds the emulator lock {p}")
        os.set_inheritable(fh.fileno(), True)
        yield fh.fileno()
    finally:
        fcntl.flock(fh, fcntl.LOCK_UN)
        fh.close()


def avd_home(env=os.environ) -> pathlib.Path:
    if env.get("ANDROID_AVD_HOME"):
        return pathlib.Path(env["ANDROID_AVD_HOME"])
    base = env.get("ANDROID_USER_HOME") or os.path.join(env.get("HOME", "~"), ".android")
    return pathlib.Path(base) / "avd"


def avd_dir(avd: str, env=os.environ) -> pathlib.Path:
    """The AVD's folder: the path= line of <avd home>/<avd>.ini when there is one (avdmanager -p puts AVDs
    elsewhere, e.g. on Storage2), else <avd home>/<avd>.avd."""
    ini = avd_home(env) / f"{avd}.ini"
    with contextlib.suppress(OSError):
        for line in ini.read_text(encoding="utf-8").splitlines():
            if line.startswith("path="):
                return pathlib.Path(line[len("path="):].strip())
    return avd_home(env) / f"{avd}.avd"


def avd_has_snapshot(avd: str, snapshot: str, env=os.environ) -> bool:
    return (avd_dir(avd, env) / "snapshots" / snapshot).is_dir()


def emulator_cmd(emulator: str, avd: str, snapshot: str | None) -> list[str]:
    cmd = [emulator, "-avd", avd, "-port", str(PORT), "-no-window", "-no-audio", "-no-boot-anim",
           "-no-snapshot-save"]
    if snapshot:
        cmd += ["-snapshot", snapshot]  # boots straight into the seeded Flutter app
    else:
        cmd += ["-wipe-data", "-no-snapshot-load"]  # fresh: Part A starts from nothing
    return cmd


def attached(adb: run.Adb) -> bool:
    out = adb(["devices"], check=False)
    return any(l.split("\t")[0] == SERIAL for l in out.splitlines() if "\t" in l)


def wait_for_boot(adb: run.Adb, proc: subprocess.Popen | None, timeout: int = 600) -> None:
    end = time.time() + timeout
    while time.time() < end:
        if proc is not None and proc.poll() is not None:
            raise Refused(f"the emulator exited with {proc.returncode} while booting")
        if attached(adb) and adb(["shell", "getprop", "sys.boot_completed"], check=False).strip() == "1":
            return
        time.sleep(3)
    raise Refused("the emulator did not finish booting in time")


def stop_emulator(adb: run.Adb, proc: subprocess.Popen) -> None:
    adb(["emu", "kill"], check=False)
    try:
        proc.wait(timeout=90)
    except subprocess.TimeoutExpired:
        proc.terminate()  # the exact process this script started, never a pattern
        with contextlib.suppress(subprocess.TimeoutExpired):
            proc.wait(timeout=30)


# ---------------------------------------------------------------- step 3: fixtures and the flow

def seed_dir(args) -> pathlib.Path:
    return pathlib.Path(args.seed_home).expanduser() / args.snapshot


def reset_owned_dir(path: pathlib.Path) -> None:
    """Empties a fixture folder this script made (marked), so Part A seeds the same data every time."""
    if path.exists():
        if not (path / OWNED_MARK).exists():
            raise Refused(f"{path} exists but was not made by upgrade_e2e; move it away or pass another --seed-home")
        shutil.rmtree(path)
    path.mkdir(parents=True)
    (path / OWNED_MARK).write_text("made by native/tool/qa/upgrade_e2e.sh\n")


def fixtures_env_from_state(state: pathlib.Path) -> dict:
    """The env fx.py wrote for run.py: the per-run passwords and FX_CTL (the flows' fixture dispatcher)."""
    data = json.loads(state.read_text(encoding="utf-8"))
    env = data.get("env", {})
    return {k: env[k] for k in ("SERVER_PASSWORD", "SERVER_B_PASSWORD", "FX_CTL") if k in env}


def fixtures_env(fx_dir: pathlib.Path) -> dict:
    secrets = {}
    path = fx_dir / "secrets.env"
    for line in path.read_text(encoding="utf-8").splitlines():
        k, sep, v = line.partition("=")
        if sep:
            secrets[k.strip()] = v.strip()
    return {"SERVER_PASSWORD": secrets.get("FX_OC2_PASSWORD", ""),
            "SERVER_B_PASSWORD": secrets.get("FX_OC2_B_PASSWORD", "")}


def write_private_json(path: pathlib.Path, data: dict) -> None:
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        json.dump(data, fh)


def plan_restore(args, seed: pathlib.Path) -> bool:
    """Restore the seeded snapshot only when its fixture state (server data, per-snapshot passwords) is there too:
    a snapshot whose servers no longer know its saved passwords would fail Part C for the wrong reason."""
    if args.reseed:
        return False
    if not (args.fixtures_env or (seed / "fx" / "secrets.env").exists()):
        return False
    if args.avd and not args.use_running:
        return avd_has_snapshot(args.avd, args.snapshot)
    return True  # --use-running: confirmed on the device with `emu avd snapshot list`


def run_py_cmd(args, out: pathlib.Path, seeded: bool, fx_env_file: pathlib.Path | None) -> list[str]:
    cmd = [sys.executable, "-I", str(HERE / "run.py"), "--app", "native", "--flows", FLOW,
           "--native-apk", os.path.abspath(args.native_apk), "--expect-signer", args.expect_signer,
           "--serial", SERIAL, "--no-retry", "--lock", args.lock, "--out", str(out / "run"),
           "--state-dir", str(out / "state"), "--maestro", args.maestro, "--adb", args.adb,
           # Part A (built-in Linux and the voice pack from the real hosts), Part B and the native part take about
           # an hour on the PC emulator; run.py's default is 3600 s.
           "--flow-timeout", str(FLOW_TIMEOUT_S)]
    if args.avd:
        cmd += ["--expect-avd", args.avd]
    if seeded:
        cmd.append("--seeded")
    else:
        cmd += ["--apk", os.path.abspath(args.flutter_apk)]
        if not args.no_save_snapshot and snapshot_space_ok(args):
            cmd += ["--save-seed-snapshot", args.snapshot]
    if fx_env_file:
        cmd += ["--fixtures-env", str(fx_env_file)]
    # run.py's RAM check runs with the gate's own AVD already booted (it would ask for 7 GB on top of it): the gate
    # checks before booting instead (preflight) and always tells run.py to skip it.
    cmd.append("--skip-resource-checks")
    return cmd


def snapshot_space_ok(args, env=os.environ) -> bool:
    """Room for the seeded snapshot (RAM image plus disk deltas) next to the AVD."""
    if not args.avd:
        return True
    target = avd_dir(args.avd, env)
    probe = target if target.exists() else target.parent
    try:
        return shutil.disk_usage(probe).free >= SNAPSHOT_FREE_BYTES
    except OSError:
        return True


def preflight(args, env=os.environ) -> None:
    """Refuse before taking the emulator: files outside native/ the flows need, and the host RAM."""
    missing = [f for f in NEEDED_FILES if not (REPO / f).exists()]
    if missing:
        raise Refused(f"this checkout lacks {', '.join(missing)} (a sparse checkout? add lib/l10n, "
                      "android/app/src/main/res and docs to it)")
    if args.skip_resource_checks or args.use_running:
        return
    with open("/proc/meminfo") as fh:
        avail = next(int(l.split()[1]) for l in fh if l.startswith("MemAvailable"))
    if avail < RAM_NEEDED_KB:
        raise Refused(f"only {avail // 1024} MB RAM available before booting the AVD; 7 GB needed "
                      "(--skip-resource-checks to override)")


# ---------------------------------------------------------------- step 4: judging

def maestro_statuses(debug_root: pathlib.Path) -> dict[str, str]:
    """{label: status} from Maestro's commands-*.json (debug output). A label seen twice keeps the worst status."""
    # E2E-39's assertion steps are optional runFlows, so the flow goes on after one fails: WARNED is a failed one.
    rank = {"FAILED": 4, "WARNED": 3, "COMPLETED": 2, "SKIPPED": 1, "PENDING": 0, "RUNNING": 0}
    found: dict[str, str] = {}

    def visit(node):
        if isinstance(node, dict):
            cmd, meta = node.get("command"), node.get("metadata")
            if isinstance(cmd, dict) and isinstance(meta, dict):
                for inner in cmd.values():
                    if isinstance(inner, dict) and isinstance(inner.get("label"), str):
                        status = str(meta.get("status", "PENDING"))
                        old = found.get(inner["label"])
                        if old is None or rank.get(status, 0) > rank.get(old, 0):
                            found[inner["label"]] = status
            for v in node.values():
                visit(v)
        elif isinstance(node, list):
            for v in node:
                visit(v)

    # Maestro 2.12 writes commands.json (one per flow, --flatten-debug-output); older versions commands-(<flow>).json.
    for f in sorted(debug_root.rglob("commands*.json")):
        with contextlib.suppress(OSError, json.JSONDecodeError):
            visit(json.loads(f.read_text(encoding="utf-8")))
    return found


def ui_verdicts(statuses: dict[str, str], flow_passed: bool) -> list[Line]:
    out = []
    for aid, title, prefixes in UI_ASSERTIONS:
        states = []
        for prefix in prefixes:
            hits = [s for label, s in statuses.items() if label == prefix or label.startswith(prefix + " ")]
            states.append(max(hits, key=lambda s: {"FAILED": 2, "WARNED": 1}.get(s, 0)) if hits else None)
        if all(s == "COMPLETED" for s in states):
            out.append(Line(aid, title, True))
        elif any(s in ("FAILED", "WARNED") for s in states):
            out.append(Line(aid, title, False, "failed on the device; see maestro.log and the failure screenshot"))
        elif flow_passed and all(s is None for s in states):
            out.append(Line(aid, title, True, "flow passed; per-step status not reported by Maestro"))
        else:
            out.append(Line(aid, title, False, "not reached: the flow stopped earlier" if not flow_passed
                            else "skipped on the device"))
    return out


def state_verdicts(state_dir: pathlib.Path) -> list[Line]:
    before, after = state_dir / "before.json", state_dir / "after-migration.json"
    if not before.exists() or not after.exists():
        missing = [p.name for p in (before, after) if not p.exists()]
        return [Line("device-state", "OS-held state read before and after the update", False,
                     f"missing {', '.join(missing)}: the flow stopped before it was read")]
    b = json.loads(before.read_text(encoding="utf-8"))
    a = json.loads(after.read_text(encoding="utf-8"))
    return [Line(v.id, v.title, v.ok, v.detail, v.not_run) for v in device_state.compare(b, a)]


def collect_screens(run_out: pathlib.Path, dest: pathlib.Path) -> list[tuple[str, str]]:
    """Copies native-<screen>.png (one per screen) and any failure screenshot; returns (file, sentence)."""
    dest.mkdir(parents=True, exist_ok=True)
    shots = []
    for png in sorted(run_out.rglob("*.png")):
        name = png.stem
        if name.startswith("native-") and name[len("native-"):] in SCREENS:
            target = dest / f"{name}.png"
            shutil.copyfile(png, target)
            shots.append((target.name, SCREENS[name[len("native-"):]]))
        elif "❌" in name or "fail" in name.lower():
            target = dest / f"failure-{len(shots) + 1}.png"
            shutil.copyfile(png, target)
            shots.append((target.name, "Where the flow stopped (Maestro's failure screenshot)."))
    # Maestro 2.12 keeps a failed run's screenshots as debug/<flow>/screenshots/step-NNN-<command>.png: the
    # highest step is where it stopped.
    steps = sorted(run_out.rglob("debug/*/screenshots/step-*.png"), key=lambda p: (str(p.parent), p.name))
    if steps and not any(f.startswith("failure-") for f, _ in shots):
        target = dest / f"failure-{len(shots) + 1}.png"
        shutil.copyfile(steps[-1], target)
        shots.append((target.name, f"Where the flow stopped ({steps[-1].stem})."))
    return shots


# ---------------------------------------------------------------- step 5: the evidence record

def evidence_dir(args, today: dt.date) -> pathlib.Path:
    if args.evidence_dir:
        return pathlib.Path(args.evidence_dir)
    base = REPO / "docs" / "qa" / f"native-switch-{today.isoformat()}"
    path, n = base, 2
    while (path / "README.md").exists():  # never overwrite an earlier record
        path = base.with_name(f"{base.name}-{n}")
        n += 1
    return path


def verdict_word(lines: list[Line]) -> str:
    """FAIL when a check failed; INCOMPLETE when every check that ran passed but some could not run (B-153)."""
    if not lines or any(l.failed for l in lines):
        return "FAIL"
    return "INCOMPLETE" if any(l.not_run for l in lines) else "PASS"


def write_record(dest: pathlib.Path, *, flutter: dict, native: dict, expect_signer: str, avd: str | None,
                 seeded_from: str | None, gates: list[Line], verdicts: list[Line],
                 shots: list[tuple[str, str]], run_out: pathlib.Path, today: dt.date) -> pathlib.Path:
    lines = gates + verdicts
    word = verdict_word(lines)
    not_run = sum(l.not_run for l in lines)
    rows = "\n".join(f"| {l.id} | {l.title} | {'**FAIL**' if l.failed else l.word} | {l.detail.replace('|', '/')} |"
                      for l in lines)
    screens = "\n".join(f"- `screens/{f}`: {s}" for f, s in shots) or "- none were saved (the flow stopped first)"
    text = f"""# Native switch upgrade gate, {today.isoformat()}

**Verdict: {word}** ({sum(l.ok for l in lines)} of {len(lines)} checks passed{f', {not_run} not run' if not_run else ''})

Written by `native/tool/qa/upgrade_e2e.sh` (E00-S09). The released Flutter app was seeded (E2E-39 Part A), checked
(Part B), replaced with the native app by `adb install -r` (never uninstalled) and checked again (Part C).

## Builds

| | Flutter (installed first) | Native (installed over it) |
|---|---|---|
| File | `{flutter['file']}` | `{native['file']}` |
| Package | `{flutter['package']}` | `{native['package']}` |
| Version | {flutter['versionName']} ({flutter['versionCode']}) | {native['versionName']} ({native['versionCode']}) |
| APK SHA-256 | `{flutter['sha256']}` | `{native['sha256']}` |
| Signer SHA-256 | `{', '.join(flutter['signers'])}` | `{', '.join(native['signers'])}` |

Expected signer: `{norm_digest(expect_signer)}`

## Device

- Emulator `{SERIAL}`, AVD `{avd or 'already running'}`, held under the emulator lock for the whole run.
- Seed: {f'restored from snapshot `{seeded_from}` (Part A not repeated)' if seeded_from else 'Part A ran on a wiped AVD'}.

## Checks

| Id | Check | Verdict | Detail |
|---|---|---|---|
{rows}

## Screens

{screens}

Run output (logs, Maestro report, device state JSON): `{run_out}` on the machine that ran it.
"""
    dest.mkdir(parents=True, exist_ok=True)
    readme = dest / "README.md"
    readme.write_text(text, encoding="utf-8")
    return readme


# ---------------------------------------------------------------- main

def parse_args(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--flutter-apk", required=True, help="the released Flutter APK (2.1.4+62)")
    ap.add_argument("--native-apk", required=True, help="the native switch APK, same signer, higher versionCode")
    ap.add_argument("--expect-signer", required=True, help="SHA-256 of the signing certificate both must carry")
    ap.add_argument("--avd", help="AVD to start on port 5584 (required unless --use-running)")
    ap.add_argument("--use-running", action="store_true",
                    help="use an emulator-5584 that is already running (it is not wiped or stopped)")
    ap.add_argument("--snapshot", default=DEFAULT_SNAPSHOT)
    ap.add_argument("--reseed", action="store_true", help="run Part A even when the snapshot exists")
    ap.add_argument("--no-save-snapshot", action="store_true", help="do not save the snapshot after Part A")
    ap.add_argument("--seed-home", default=os.environ.get("OC_E2E_SEED_HOME", "~/.cache/oc-e2e/seeds"),
                    help="where each snapshot's fixture state (and per-snapshot fixture passwords) is kept")
    ap.add_argument("--fixtures-env", help="JSON env of fixtures someone already started (skips fx.py)")
    ap.add_argument("--lock", default=run.DEFAULT_LOCK, help="the emulator lock (OC_EMULATOR_LOCK)")
    ap.add_argument("--out", type=pathlib.Path, help="run output (default native/e2e/runner/out/upgrade-<stamp>)")
    ap.add_argument("--evidence-dir", help="default docs/qa/native-switch-<date> (a -2, -3 suffix if it exists)")
    ap.add_argument("--emulator", default=os.environ.get("EMULATOR", "emulator"))
    ap.add_argument("--adb", default=os.environ.get("ADB", "adb"))
    ap.add_argument("--maestro", default=os.environ.get("MAESTRO", "maestro"))
    ap.add_argument("--keep-emulator", action="store_true", help="leave the AVD running afterwards")
    ap.add_argument("--skip-resource-checks", action="store_true")
    ap.add_argument("--dry-run", action="store_true", help="check the APKs and print the plan; touch no device")
    return ap.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    today = dt.date.today()
    aapt2, apksigner = find_tool("aapt2"), find_tool("apksigner")
    if not aapt2 or not apksigner:
        print("FAIL tools: aapt2 and apksigner are needed (PATH or $ANDROID_HOME/build-tools)")
        return 2
    try:
        flutter = apk_facts(args.flutter_apk, aapt2, apksigner)
        native = apk_facts(args.native_apk, aapt2, apksigner)
    except Refused as e:
        print(f"FAIL apks: {e}")
        return 2
    gates = [say(l) for l in apk_gates(flutter, native, args.expect_signer)]
    if not all(l.ok for l in gates):
        print("REFUSED: the native APK cannot replace this Flutter APK in place")
        return 2
    if not args.avd and not args.use_running:
        print("FAIL emulator: pass --avd <name> (a fresh AVD on port 5584) or --use-running")
        return 2

    adb = run.Adb(SERIAL, args.adb)
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    out = (args.out or HERE / "out" / f"upgrade-{stamp}").resolve()
    seed = seed_dir(args)
    fx_dir = seed / "fx"
    restore = plan_restore(args, seed)
    if args.dry_run:
        print(json.dumps({"serial": SERIAL, "avd": args.avd, "snapshot": args.snapshot, "restore": restore,
                          "seedHome": str(seed), "out": str(out),
                          "emulator": None if args.use_running else emulator_cmd(
                              args.emulator, args.avd, args.snapshot if restore else None),
                          "runPy": run_py_cmd(args, out, restore, None)}, indent=1))
        return 0

    try:
        preflight(args)
    except Refused as e:
        print(f"FAIL setup: {e}")
        return 2
    out.mkdir(parents=True, exist_ok=True)
    proc = None
    fx_started = False
    verdicts: list[Line] = []
    try:
        with hold_lock(args.lock) as lock_fd:
            say(Line("lock", "Emulator lock held", True, args.lock))
            if args.use_running:
                if not attached(adb):
                    raise Refused(f"{SERIAL} is not running")
            else:
                if attached(adb):
                    raise Refused(f"{SERIAL} is already running; stop it or pass --use-running")
                if shutil.which(args.emulator) is None and not os.path.exists(args.emulator):
                    raise Refused("the Android emulator binary was not found (EMULATOR or --emulator)")
                cmd = emulator_cmd(args.emulator, args.avd, args.snapshot if restore else None)
                with open(out / "emulator.log", "w") as log:
                    proc = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
            wait_for_boot(adb, proc)
            run.verify_device(SERIAL, adb, args.avd)
            # Root now, before Maestro connects: the state hooks need it, and `adb root` restarts adbd, which would
            # drop Maestro's connection if it first happened mid-flow. Later `adb root` calls are no-ops.
            adb(["root"], timeout=60)
            adb(["wait-for-device"], timeout=120)
            if restore and args.use_running:
                listed = adb(["emu", "avd", "snapshot", "list"], check=False)
                if re.search(r"(^|\s)" + re.escape(args.snapshot) + r"(\s|$)", listed):
                    adb(["emu", "avd", "snapshot", "load", args.snapshot], timeout=300)
                    wait_for_boot(adb, None)
                else:
                    restore = False
            say(Line("emulator", f"{SERIAL} booted and verified as an emulator", True,
                     f"restored snapshot {args.snapshot}" if restore else "no seeded snapshot: Part A will seed"))

            if args.fixtures_env:
                fx_env_file = pathlib.Path(args.fixtures_env).resolve()
            else:
                if not restore:
                    reset_owned_dir(seed)
                fx_dir.mkdir(parents=True, exist_ok=True)
                fx_started = True
                rc = subprocess.run([sys.executable, "-I", str(FX), "start", "--ids", ",".join(FIXTURES),
                                     "--run-dir", str(fx_dir), "--state", str(fx_dir / "fixtures-state.json")]
                                    ).returncode
                if rc != 0:
                    raise Refused("a fixture did not start (see the fxctl lines above)")
                fx_env_file = fx_dir / "fixtures-env.json"
                write_private_json(fx_env_file, {"env": fixtures_env_from_state(fx_dir / "fixtures-state.json")})
            say(Line("fixtures", "E2E-39 fixtures ready", True, "given" if args.fixtures_env else str(fx_dir)))

            env = {**os.environ, "OC_EMULATOR_LOCK_FD": str(lock_fd)}
            run_rc = subprocess.run(run_py_cmd(args, out, restore, fx_env_file), env=env, cwd=str(out),
                                    pass_fds=(lock_fd,)).returncode
            if run_rc == 2 and not (out / "state" / "before.json").exists():
                raise Refused("run.py could not run the flow (see its refusal above)")
    except Refused as e:
        print(f"FAIL setup: {e}")
        _shutdown(adb, proc, args, fx_started, fx_dir)
        return 2
    _shutdown(adb, proc, args, fx_started, fx_dir)

    flows = []
    with contextlib.suppress(OSError, json.JSONDecodeError):
        flows = json.loads((out / "run" / "flows.json").read_text(encoding="utf-8"))
    flow_passed = bool(flows) and all(f.get("result") == "PASSED" for f in flows)
    summary = (out / "run" / "summary.txt")
    leak = summary.exists() and "SECRET IN LOGCAT" in summary.read_text(encoding="utf-8")
    verdicts += [say(l) for l in ui_verdicts(maestro_statuses(out / "run"), flow_passed)]
    verdicts += [say(l) for l in state_verdicts(out / "state")]
    # Assertion steps are optional (a failed one is WARNED and the flow goes on), so a PASSED flow means it ran to
    # the end, not that every assertion held: those are the lines above.
    verdicts.append(say(Line("flow", "E2E-39 ran to the end (assertions are judged one by one above)", flow_passed,
                             ", ".join(f"{f['flow']} {f['result']}" for f in flows) or "no result")))
    if leak:
        verdicts.append(say(Line("log-scan", "No fixture password or canary in logcat", False, "found in logcat")))

    dest = evidence_dir(args, today)
    shots = collect_screens(out / "run", dest / "screens")
    readme = write_record(dest, flutter=flutter, native=native, expect_signer=args.expect_signer, avd=args.avd,
                          seeded_from=args.snapshot if restore else None, gates=gates, verdicts=verdicts,
                          shots=shots, run_out=out, today=today)
    failed = [l.id for l in verdicts if l.failed]
    not_run = [l.id for l in verdicts if l.not_run]
    print(f"evidence: {readme}")
    print(f"VERDICT: {verdict_word(gates + verdicts)}" + (f" ({', '.join(failed)})" if failed else "")
          + (f"; not run: {', '.join(not_run)}" if not_run else ""))
    return 1 if failed or not_run else 0


def _shutdown(adb, proc, args, fx_started, fx_dir) -> None:
    if fx_started:
        subprocess.run([sys.executable, "-I", str(FX), "stop", "--state", str(fx_dir / "fixtures-state.json")],
                       check=False)
        for name in ("fixtures-env.json", "fixtures-state.json"):
            with contextlib.suppress(FileNotFoundError):
                (fx_dir / name).unlink()
    if proc is not None and not args.keep_emulator and proc.poll() is None:
        stop_emulator(adb, proc)


if __name__ == "__main__":
    sys.exit(main())
