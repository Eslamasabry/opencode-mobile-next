"""Allowlisted host-side hooks for the [adb] steps of the flows.

A flow calls a hook with
    # adb: <name>
    - runScript:
        file: ../_hooks/adb.js
        env: {HOOK: <name>, ARGS: '<json>'}
adb.js POSTs to the runner's hook server on 127.0.0.1 (per-run token). The server runs ONLY the hooks below, ONLY
with `adb -s <verified emulator serial>`, and returns a small JSON result (never device text that could hold a
secret). Maestro cannot run host commands itself, so this replaces plan 14 §4.2 rule 8's comment-only hooks.

Every argument is validated against a fixed pattern; shell text is quoted with shlex.quote.
"""
from __future__ import annotations

import dataclasses
import re
import shlex
from typing import Callable


class HookError(Exception):
    pass


@dataclasses.dataclass(frozen=True)
class Hook:
    doc: str
    plan: Callable[[dict, str], list[list[str]]] | None = None  # adb argument lists (without -s)
    root: bool = False          # needs `adb root` (google_apis emulator images allow it)
    host: str | None = None     # handled by run.py itself (name of the handler)
    verified: bool = True       # False: the recipe is [U] until the first PC run confirms it


_PERM = re.compile(r"^[A-Z_]{3,40}$")
_WORD = re.compile(r"^[A-Za-z0-9._-]{1,64}$")
_LOCALES = re.compile(r"^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*(,[a-z]{2,3}(-[A-Za-z0-9]{2,8})*){0,4}$")
_ONOFF = {"on": "enable", "off": "disable"}


def _need(args: dict, key: str, pattern: re.Pattern | None = None, kind=str):
    if key not in args:
        raise HookError(f"missing argument {key}")
    v = args[key]
    if not isinstance(v, kind) or isinstance(v, bool) and kind is not bool:
        raise HookError(f"argument {key} has the wrong type")
    if pattern is not None and not pattern.match(str(v)):
        raise HookError(f"argument {key} is not allowed")
    return v


def _net(args, app_id):
    cmds = []
    for radio in ("wifi", "data"):
        if radio in args:
            if args[radio] not in _ONOFF:
                raise HookError(f"{radio} must be on or off")
            cmds.append(["shell", "svc", radio, _ONOFF[args[radio]]])
    if not cmds:
        raise HookError("net needs wifi and/or data")
    return cmds


def _airplane(args, app_id):
    on = _need(args, "on", kind=bool)
    return [["shell", "cmd", "connectivity", "airplane-mode", "enable" if on else "disable"]]


def _permission(args, app_id):
    perm = _need(args, "perm", _PERM)
    grant = _need(args, "grant", kind=bool)
    return [["shell", "pm", "grant" if grant else "revoke", app_id, "android.permission." + perm]]


def _appop(args, app_id):
    op = _need(args, "op", _PERM)
    mode = _need(args, "mode", re.compile(r"^(allow|deny|ignore|default)$"))
    return [["shell", "appops", "set", app_id, op, mode]]


def _shade(args, app_id):
    return [["shell", "cmd", "statusbar", "expand-notifications" if _need(args, "open", kind=bool) else "collapse"]]


def _qs_tile(args, app_id):
    action = _need(args, "action", re.compile(r"^(add|remove|click)$"))
    comp = _need(args, "component", re.compile(r"^\.[A-Za-z0-9_.]{1,80}$"))
    return [["shell", "cmd", "statusbar", action + "-tile", f"{app_id}/{app_id}{comp}"]]


def _share_text(args, app_id):
    text = _need(args, "text")
    if len(text) > 2000 or any(ord(c) < 32 and c not in "\n\t" for c in text):
        raise HookError("text too long or has control characters")
    cmd = ["shell", "am", "start", "-a", "android.intent.action.SEND", "-t", "text/plain", "-p", app_id]
    if text:
        cmd += ["--es", "android.intent.extra.TEXT", shlex.quote(text)]
    return [cmd]


def _view_link(args, app_id):
    uri = _need(args, "uri")
    if len(uri) > 4096 or any(ord(c) < 33 for c in uri):
        raise HookError("uri not allowed")
    return [["shell", "am", "start", "-a", "android.intent.action.VIEW", "-p", app_id, "-d", shlex.quote(uri)]]


def _screen(args, app_id):
    on = _need(args, "on", kind=bool)
    if on:
        return [["shell", "input", "keyevent", "224"], ["shell", "wm", "dismiss-keyguard"]]
    return [["shell", "input", "keyevent", "223"]]


def _locale(args, app_id):
    tags = _need(args, "tags", _LOCALES)
    first = tags.split(",")[0]
    return [["shell", "settings", "put", "system", "system_locales", tags],
            ["shell", "setprop", "persist.sys.locale", first],
            ["shell", "setprop", "ctl.restart", "zygote"]]


def _clock(args, app_id):
    minutes = _need(args, "advance_minutes", kind=int)
    if not 0 < minutes <= 24 * 60 * 3:
        raise HookError("advance_minutes out of range")
    # `date -s` with an absolute time is computed on the device from its own clock.
    return [["shell", "settings", "put", "global", "auto_time", "0"],
            ["shell", f"date @$(( $(date +%s) + {minutes * 60} ))"]]


def _clock_restore(args, app_id):
    return [["shell", "settings", "put", "global", "auto_time", "1"]]


def _dumpsys_count(args, app_id):
    service = _need(args, "service", re.compile(r"^(alarm|notification|activity)$"))
    return [["shell", "dumpsys", service]]


def _corrupt(args, app_id):
    name = _need(args, "file", re.compile(r"^(shared_prefs/[A-Za-z0-9_.]+\.xml|files/[A-Za-z0-9_./-]+)$"))
    if ".." in name:
        raise HookError("path not allowed")
    return [["shell", f"echo garbage > /data/data/{app_id}/{name}"]]


def _ps_count(args, app_id):
    _need(args, "match", _WORD)
    return [["shell", "ps", "-A", "-o", "PID,USER,ARGS"]]


HOOKS: dict[str, Hook] = {
    "net": Hook("Wi-Fi and/or mobile data on/off: {wifi?: on|off, data?: on|off}", _net),
    "airplane": Hook("Airplane mode: {on: bool}", _airplane),
    "permission": Hook("Grant or revoke a runtime permission of the app: {perm: CAMERA, grant: bool}", _permission),
    "appop": Hook("Set an app-op for the app: {op: SCHEDULE_EXACT_ALARM, mode: allow|deny|ignore|default}", _appop),
    "shade": Hook("Open or close the notification shade: {open: bool}", _shade),
    "qs-tile": Hook("Add, remove or click the app's Quick Settings tile: {action, component: .AttentionTileService}",
                    _qs_tile, verified=False),
    "share-text": Hook("Send text into the app with ACTION_SEND: {text}", _share_text),
    "view-link": Hook("Open a VIEW intent restricted to the app (deep link, cold or warm): {uri}", _view_link),
    "screen": Hook("Screen off (lock) or on + dismiss keyguard: {on: bool}", _screen),
    "locale": Hook("Set the system locale list and restart the framework: {tags: 'sv-SE,ja-JP'}", _locale,
                   root=True, verified=False),
    "clock-advance": Hook("Move the device clock forward: {advance_minutes: int}", _clock, root=True),
    "clock-restore": Hook("Turn automatic time back on: {}", _clock_restore),
    "dumpsys-count": Hook("Count lines of dumpsys <service> that contain the app id and {match}: "
                          "{service: alarm|notification|activity, match}; returns {count}", _dumpsys_count,
                          host="dumpsys_count"),
    "corrupt-app-file": Hook("Overwrite one app data file with garbage: {file: shared_prefs/x.xml|files/...}",
                             _corrupt, root=True),
    "ps-count": Hook("Count processes of the app's uid whose command line contains {match}; returns {count}",
                     _ps_count, host="ps_count"),
    "kill-app-proc": Hook("kill -9 the exact PIDs of the app's uid whose command line contains {match} "
                          "(never by pattern outside the app uid); returns {killed}", root=True, host="kill_app_proc"),
    "logcat-scan": Hook("Scan `logcat -d` for the canary secrets and per-run passwords; returns "
                        "{found: bool} and never the log text", host="logcat_scan"),
    "device-http": Hook("Forward a device port and make one HTTP request from the host: {port, path, method}; "
                        "returns {status, disposition, bytes}", host="device_http"),
    "install-native": Hook("adb install -r the native APK given to run.py with --native-apk (never uninstall, "
                           "never -d); returns {installed: bool}", host="install_native"),
    "fill-disk": Hook("Fill /data with a file until {leave_mb} MB are free; removed at cleanup", host="fill_disk"),
    "unfill-disk": Hook("Remove the fill file", host="unfill_disk"),
    "push-media": Hook("Push the fixture device files (photos, data.xlsx, report.docx, notes.txt, name-only stub) to "
                       "/sdcard/Download and scan them", host="push_media"),
    "camera-scene": Hook("Show {image: qr-pairing|qr-other|none} to the emulator's virtual camera", host="camera_scene",
                         verified=False),
    "mic-feed": Hook("Feed {wav: speech-3min|phrase-6s|speech-3min-broken} to the emulator microphone",
                     host="mic_feed", verified=False),
}


def plan(name: str, args: dict, app_id: str) -> list[list[str]]:
    hook = HOOKS.get(name)
    if hook is None:
        raise HookError(f"unknown hook {name}")
    if hook.plan is None:
        raise HookError(f"hook {name} is handled by the runner")
    if not isinstance(args, dict):
        raise HookError("ARGS must be a JSON object")
    return hook.plan(args, app_id)
