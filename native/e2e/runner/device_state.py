"""OS-held app state the upgrade must keep (E00-S09, plan 05 §10.4 step 6 and §10.5 J1).

capture(adb, app_id) reads, on the verified emulator only and as root:
  - runtime permission grants (dumpsys package) and special app-ops (appops get)
  - notification channels with their importance (dumpsys notification --noredact)
  - launcher shortcuts, pinned and dynamic (dumpsys shortcut)
  - placed home-screen widgets by provider (dumpsys appwidget)
  - the app's Quick Settings tiles (settings get secure sysui_qs_tiles)
  - the app's alarms (dumpsys alarm): a scheduled prompt keeps one armed
  - the built-in Linux: ubuntu.ready and the rootfs inode count (volatile folders left out)
  - the voice pack: every file under files/voice_models with its size

The result is plain JSON with names, ids and counts only: never file contents, never a notification's text, never
a secret. compare(before, after) turns two captures into host-side assertions with a PASS or FAIL verdict each.

Standard library only; the parsers are pure functions so the unit tests feed them recorded dumpsys text.
"""
from __future__ import annotations

import dataclasses
import re

# Folders whose inode count legitimately changes while Linux or its server runs; everything else must match.
ROOTFS_VOLATILE = ("tmp", "var/tmp", "run", "var/run", "var/log", "var/cache", "proc", "sys", "dev")
LINUX_DIR = "files/linux"
ROOTFS_DIR = "files/linux/ubuntu"
VOICE_DIR = "files/voice_models"


# ---------------------------------------------------------------- parsers (pure)

def parse_runtime_grants(dumpsys_package: str) -> dict[str, bool]:
    """`dumpsys package <pkg>`: the `runtime permissions:` block(s) -> {permission: granted}."""
    grants: dict[str, bool] = {}
    inside = False
    indent = 0
    for line in dumpsys_package.splitlines():
        stripped = line.strip()
        if stripped.startswith("runtime permissions:"):
            inside, indent = True, len(line) - len(line.lstrip())
            continue
        if inside:
            if not stripped or len(line) - len(line.lstrip()) <= indent:
                inside = False
                continue
            m = re.match(r"^([A-Za-z0-9_.]+): granted=(true|false)", stripped)
            if m:
                grants[m.group(1)] = m.group(2) == "true"
    return grants


def parse_install_grants(dumpsys_package: str) -> dict[str, bool]:
    """`dumpsys package <pkg>`: the `install permissions:` block -> {permission: granted}."""
    grants: dict[str, bool] = {}
    inside = False
    indent = 0
    for line in dumpsys_package.splitlines():
        stripped = line.strip()
        if stripped.startswith("install permissions:"):
            inside, indent = True, len(line) - len(line.lstrip())
            continue
        if inside:
            if not stripped or len(line) - len(line.lstrip()) <= indent:
                inside = False
                continue
            m = re.match(r"^([A-Za-z0-9_.]+): granted=(true|false)", stripped)
            if m:
                grants[m.group(1)] = m.group(2) == "true"
    return grants


def parse_version_code(dumpsys_package: str) -> int | None:
    m = re.search(r"versionCode=(\d+)", dumpsys_package)
    return int(m.group(1)) if m else None


def parse_appops(appops_get: str) -> dict[str, str]:
    """`appops get <pkg>` -> {op: mode}. Times and durations are dropped: only the choice matters."""
    ops: dict[str, str] = {}
    for line in appops_get.splitlines():
        m = re.match(r"^\s*([A-Z_][A-Z0-9_]+)(?: \(\w+\))?: ([a-z_]+)", line)
        if m:
            ops[m.group(1)] = m.group(2)
    return ops


def _package_block(text: str, start: re.Pattern, next_start: re.Pattern) -> list[str]:
    lines = text.splitlines()
    out: list[str] = []
    inside = False
    for line in lines:
        if start.search(line):
            inside = True
            continue
        if inside and next_start.search(line):
            inside = False
        if inside:
            out.append(line)
    return out


def parse_channels(dumpsys_notification: str, app_id: str) -> dict[str, int]:
    """`dumpsys notification --noredact` -> {channel id: importance} for the app (AppSettings block)."""
    block = _package_block(dumpsys_notification,
                           re.compile(r"AppSettings: " + re.escape(app_id) + r" \("),
                           re.compile(r"AppSettings: "))
    channels: dict[str, int] = {}
    for line in block:
        for m in re.finditer(r"NotificationChannel\{mId='([^']*)'.*?mImportance=(-?\d+)", line):
            channels[m.group(1)] = int(m.group(2))
    return channels


def parse_shortcuts(dumpsys_shortcut: str, app_id: str) -> dict[str, list[str]]:
    """`dumpsys shortcut` -> {"pinned": [ids], "dynamic": [ids]} for the app's package block."""
    block = _package_block(dumpsys_shortcut,
                           re.compile(r"^\s*Package: " + re.escape(app_id) + r"\s*$|^\s*Package: "
                                      + re.escape(app_id) + r" "),
                           re.compile(r"^\s*(Package|Launcher): "))
    pinned: set[str] = set()
    dynamic: set[str] = set()
    for line in block:
        m = re.search(r"ShortcutInfo \{id=([^,]+), flags=0x[0-9a-fA-F]+ \[([^\]]*)\]", line)
        if not m:
            continue
        flags = m.group(2).split()
        if "Pin" in flags:
            pinned.add(m.group(1))
        if "Dyn" in flags:
            dynamic.add(m.group(1))
    return {"pinned": sorted(pinned), "dynamic": sorted(dynamic)}


def parse_widgets(dumpsys_appwidget: str, app_id: str) -> dict[str, str]:
    """`dumpsys appwidget` -> {widget id: provider class} for widgets whose provider is in the app."""
    widgets: dict[str, str] = {}
    current: str | None = None
    for line in dumpsys_appwidget.splitlines():
        m = re.match(r"^\s*\[\d+\] id=(\d+)", line)
        if m:
            current = m.group(1)
            continue
        if current is None:
            continue
        m = re.search(r"provider=ProviderId\{.*?cmp:ComponentInfo\{" + re.escape(app_id) + r"/([^}]+)\}", line)
        if m:
            widgets[current] = m.group(1)
            current = None
    return widgets


def parse_tiles(sysui_qs_tiles: str, app_id: str) -> list[str]:
    """`settings get secure sysui_qs_tiles` -> the app's tile components, in the user's order."""
    tiles = []
    for entry in sysui_qs_tiles.strip().split(","):
        m = re.match(r"^custom\(" + re.escape(app_id) + r"/(.+)\)$", entry.strip())
        if m:
            tiles.append(m.group(1))
    return tiles


def parse_alarm_count(dumpsys_alarm: str, app_id: str) -> int:
    """`dumpsys alarm` -> how many pending alarms the app holds (one line per alarm record)."""
    return sum(1 for line in dumpsys_alarm.splitlines()
               if re.search(r"Alarm\{[0-9a-f]+ type -?\d+ (?:origWhen -?\d+ )?" + re.escape(app_id) + r"\}", line))


def parse_file_sizes(stat_out: str) -> dict[str, int]:
    """`find DIR -type f -exec stat -c '%s %n' {} +` -> {relative path: size}."""
    files: dict[str, int] = {}
    for line in stat_out.splitlines():
        m = re.match(r"^(\d+) (.+)$", line.strip())
        if m:
            files[m.group(2)] = int(m.group(1))
    return files


def inode_count_command(app_id: str) -> str:
    """One shell line (run as root) counting the rootfs inodes, volatile folders pruned, on one filesystem."""
    root = f"/data/data/{app_id}/{ROOTFS_DIR}"
    prunes = " -o ".join(f"-path {root}/{v}" for v in ROOTFS_VOLATILE)
    return f"[ -d {root} ] && find {root} -xdev \\( {prunes} \\) -prune -o -print | wc -l || echo -1"


# ---------------------------------------------------------------- capture (device)

def capture(adb, app_id: str) -> dict:
    """Reads the state above from the device. `adb` is run.Adb bound to the verified serial (root already on)."""
    if not re.match(r"^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$", app_id):
        raise ValueError("app id not allowed")
    pkg = adb(["shell", "dumpsys", "package", app_id])
    data = f"/data/data/{app_id}"
    voice = adb(["shell", f"cd {data} && [ -d {VOICE_DIR} ] && find {VOICE_DIR} -type f -exec stat -c '%s %n' {{}} + "
                          f"|| true"], check=False)
    inodes = adb(["shell", inode_count_command(app_id)], check=False).strip().splitlines()
    ready = adb(["shell", f"[ -f {data}/{LINUX_DIR}/ubuntu.ready ] && echo yes || echo no"], check=False).strip()
    return {
        "versionCode": parse_version_code(pkg),
        "runtimeGrants": parse_runtime_grants(pkg),
        "installGrants": parse_install_grants(pkg),
        "appops": parse_appops(adb(["shell", "appops", "get", app_id], check=False)),
        "channels": parse_channels(adb(["shell", "dumpsys", "notification", "--noredact"]), app_id),
        "shortcuts": parse_shortcuts(adb(["shell", "dumpsys", "shortcut"]), app_id),
        "widgets": parse_widgets(adb(["shell", "dumpsys", "appwidget"]), app_id),
        "tiles": parse_tiles(adb(["shell", "settings", "get", "secure", "sysui_qs_tiles"], check=False), app_id),
        "alarms": parse_alarm_count(adb(["shell", "dumpsys", "alarm"]), app_id),
        "linuxReady": ready == "yes",
        "rootfsInodes": int(inodes[-1]) if inodes and re.match(r"^-?\d+$", inodes[-1].strip()) else -1,
        "voiceFiles": parse_file_sizes(voice),
    }


# ---------------------------------------------------------------- compare

@dataclasses.dataclass
class Verdict:
    id: str
    title: str
    ok: bool
    detail: str

    @property
    def word(self) -> str:
        return "PASS" if self.ok else "FAIL"


def _diff(a: dict, b: dict) -> str:
    gone = sorted(set(a) - set(b))
    added = sorted(set(b) - set(a))
    changed = sorted(k for k in set(a) & set(b) if a[k] != b[k])
    parts = []
    if gone:
        parts.append(f"missing after: {', '.join(map(str, gone))}")
    if added:
        parts.append(f"new after: {', '.join(map(str, added))}")
    if changed:
        parts.append("changed: " + ", ".join(f"{k} {a[k]} -> {b[k]}" for k in changed))
    return "; ".join(parts) or "identical"


def compare(before: dict, after: dict) -> list[Verdict]:
    """Host-side assertions of Part C (E00-S09). A thing Part A did not set up is a FAIL, never a skip."""
    out: list[Verdict] = []

    b_in, a_in = before.get("rootfsInodes", -1), after.get("rootfsInodes", -1)
    ok = b_in > 0 and b_in == a_in and before.get("linuxReady") and after.get("linuxReady")
    detail = (f"ubuntu.ready {before.get('linuxReady')} -> {after.get('linuxReady')}; rootfs inodes {b_in} -> {a_in}"
              + ("" if b_in > 0 else " (Part A left no built-in Linux)"))
    out.append(Verdict("linux-inodes", "Linux inode count unchanged", bool(ok), detail))

    bv, av = before.get("voiceFiles", {}), after.get("voiceFiles", {})
    ok = bool(bv) and bv == av
    out.append(Verdict("voice-files", "Voice pack files kept, not downloaded again", ok,
                       (f"{len(bv)} files, {sum(bv.values())} bytes; " + _diff(bv, av)) if bv
                       else "Part A left no voice pack"))

    ok = before.get("alarms", 0) > 0 and after.get("alarms", 0) > 0
    out.append(Verdict("schedule-armed", "Scheduled prompt still has an alarm armed", ok,
                       f"app alarms {before.get('alarms', 0)} -> {after.get('alarms', 0)}"))

    bw, aw = before.get("widgets", {}), after.get("widgets", {})
    out.append(Verdict("widgets", "Placed widgets kept", bool(bw) and bw == aw,
                       _diff(bw, aw) if bw else "Part A placed no widget"))
    bt, at = before.get("tiles", []), after.get("tiles", [])
    out.append(Verdict("tiles", "Quick Settings tiles kept", bool(bt) and bt == at,
                       f"{bt} -> {at}" if bt else "Part A added no tile"))
    bs, as_ = before.get("shortcuts", {}), after.get("shortcuts", {})
    pins_b, pins_a = set(bs.get("pinned", [])), set(as_.get("pinned", []))
    dyn_b, dyn_a = set(bs.get("dynamic", [])), set(as_.get("dynamic", []))
    ok = bool(pins_b or dyn_b) and pins_b == pins_a and dyn_b <= dyn_a
    out.append(Verdict("shortcuts", "Launcher shortcuts kept", ok,
                       f"pinned {sorted(pins_b)} -> {sorted(pins_a)}; dynamic {sorted(dyn_b)} -> {sorted(dyn_a)}"
                       if pins_b or dyn_b else "Part A left no shortcut"))

    # B-150: the update may add channels of its own; every channel the old app had keeps its id and the person's
    # importance. A removed or changed channel fails; additions are listed in the detail only.
    bc, ac = before.get("channels", {}), after.get("channels", {})
    kept = all(k in ac and ac[k] == v for k, v in bc.items())
    out.append(Verdict("channels", "Every old notification channel kept with its importance", bool(bc) and kept,
                       _diff(bc, ac) if bc else "no channel before the update"))

    bg, ag = before.get("runtimeGrants", {}), after.get("runtimeGrants", {})
    granted_b = {k for k, v in bg.items() if v}
    granted_a = {k for k, v in ag.items() if v}
    bo, ao = before.get("appops", {}), after.get("appops", {})
    ops_same = all(ao.get(k) == v for k, v in bo.items())
    ok = granted_b == granted_a and ops_same
    detail = f"granted {sorted(granted_b)}"
    if granted_b != granted_a:
        detail = f"granted before {sorted(granted_b)}, after {sorted(granted_a)}"
    if not ops_same:
        detail += "; app-ops " + _diff({k: v for k, v in bo.items()}, {k: ao.get(k) for k in bo})
    out.append(Verdict("grants", "Permission grants and app-ops identical", ok, detail))

    bvc, avc = before.get("versionCode"), after.get("versionCode")
    out.append(Verdict("updated-in-place", "Updated in place to the native version code",
                       bvc is not None and avc is not None and avc > bvc, f"versionCode {bvc} -> {avc}"))
    return out
