#!/usr/bin/env python3
"""ARM phone smoke before a release (story E02-S92). Usage: tool/qa/README.md.

Safety rules this file enforces, not just documents:
  * `adb install -r` runs only after the installed app's signer equals the
    APK's signer (installed base.apk pulled and read with apksigner);
  * no adb command may uninstall, clear app data or capture the screen;
  * every tap, text entry, swipe and key press first checks that the
    foreground package is the app (an incoming call once stole a tap);
  * nodes are found by visible text or content-desc, never by fixed
    coordinates;
  * only fixed check names, expected tokens and signer digests are printed,
    never arbitrary screen text.
"""
import argparse
import fcntl
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

PACKAGE = 'io.github.eslamasabry.opencode_mobile'
ACTIVITY = f'{PACKAGE}/.MainActivity'

DEFAULTS = {
    'phone_path': 'Settings>Servers',
    'phone_label': 'On this phone',
    'connected_label': 'Connected',
    'new_chat_path': 'New conversation>armsmoke',
    'send_label': 'Send',
    'delete_path': 'More options>Delete conversation>Delete',
}

EXIT_FAIL, EXIT_REFUSED, EXIT_FOREGROUND = 1, 2, 3

_BIDI = re.compile(r'[‎‏‪-‮⁦-⁩]')
_BOUNDS = re.compile(r'^\[(-?[0-9]+),(-?[0-9]+)\]\[(-?[0-9]+),(-?[0-9]+)\]$')
_SERIAL = re.compile(r'^[A-Za-z0-9._:-]{1,64}$')
_DIGEST = re.compile(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F:]{64,95})')
_TYPABLE = re.compile(r"^[A-Za-z0-9 .,:;/&|>=_-]+$")
_ARCH = re.compile(r'\b(aarch64|arm64|armv8l|armv7l)\b')
_GIT = re.compile(r'\bgit version [0-9]+\.[0-9]+')
_OK = re.compile(r'^ok[.!]?$', re.IGNORECASE)


class Refused(Exception):
    """A precondition failed before the app was touched."""


class ForegroundStolen(Exception):
    """Another app (a call, a system dialog) holds the foreground."""


class CheckFailed(Exception):
    """One smoke check did not see what it expected."""


def guard_command(args):
    """Refuse any adb command that could lose data or capture the screen."""
    words = []
    for arg in args:
        words.extend(str(arg).replace(';', ' ').replace('&', ' ').split())
    lowered = [w.lower() for w in words]
    if 'uninstall' in lowered:
        raise Refused('refusing an uninstall command')
    for i, word in enumerate(lowered):
        if word in ('pm', 'cmd') and 'clear' in lowered[i + 1:]:
            raise Refused('refusing to clear app data')
    if 'screencap' in lowered or 'screenrecord' in lowered:
        raise Refused('refusing to capture the screen (owner rule: text only)')


class Adb:
    def __init__(self, serial, adb='adb', runner=subprocess.run):
        if not _SERIAL.fullmatch(serial):
            raise Refused('adb serial has unexpected characters')
        self.serial, self.adb, self.runner = serial, adb, runner

    def run(self, *args, timeout=60, check=True):
        guard_command(args)
        try:
            done = self.runner([self.adb, '-s', self.serial, *map(str, args)],
                               capture_output=True, timeout=timeout)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise CheckFailed(f'adb {args[0]} did not finish') from error
        out = done.stdout.decode('utf-8', 'replace') if isinstance(done.stdout, bytes) \
            else (done.stdout or '')
        if check and done.returncode != 0:
            raise CheckFailed(f'adb {args[0]} exited {done.returncode}')
        return out


def apk_signers(apk, apksigner='apksigner', runner=subprocess.run):
    """SHA-256 digests of an APK's signers, lowercase hex without colons."""
    try:
        done = runner([apksigner, 'verify', '--print-certs', str(apk)],
                      capture_output=True, timeout=120)
    except OSError as error:
        raise Refused('apksigner is not available (set APKSIGNER)') from error
    out = done.stdout.decode('utf-8', 'replace') if isinstance(done.stdout, bytes) \
        else (done.stdout or '')
    if done.returncode != 0:
        raise Refused(f'apksigner could not verify {Path(apk).name}')
    return frozenset(m.replace(':', '').lower() for m in _DIGEST.findall(out))


def short(digests):
    return ','.join(sorted(d[:8].upper() + '...' + d[-4:].upper() for d in digests)) or 'none'


def install_checked(adb, apk, apksigner='apksigner', runner=subprocess.run):
    """Install with `install -r` only when the installed signer is the APK's."""
    apk = Path(apk)
    if not apk.is_file():
        raise Refused(f'no APK at {apk}')
    paths = [line.strip()[len('package:'):]
             for line in adb.run('shell', 'pm', 'path', PACKAGE, check=False).splitlines()
             if line.strip().startswith('package:')]
    base = next((p for p in paths if p.endswith('/base.apk')), None)
    if base is None:
        raise Refused('the app is not installed on this phone; install and set it up by hand '
                      'once (see tool/qa/README.md), then run the smoke again')
    new = apk_signers(apk, apksigner, runner)
    with tempfile.TemporaryDirectory(prefix='phone-smoke-') as work:
        pulled = Path(work) / 'installed-base.apk'
        adb.run('pull', base, str(pulled), timeout=300)
        installed = apk_signers(pulled, apksigner, runner)
    if not new or not installed or new != installed:
        raise Refused(f'signer mismatch: installed {short(installed)}, APK {short(new)}; '
                      'not installing (Android would refuse the update)')
    out = adb.run('install', '-r', str(apk), timeout=900, check=False)
    if 'Success' not in out:
        raise CheckFailed('adb install -r did not report Success')
    return new


def foreground_package(adb):
    """Package (or window name) holding input focus; None while in transition."""
    out = adb.run('shell', 'dumpsys', 'window', timeout=20, check=False)
    line = next((l.split('mCurrentFocus=', 1)[1] for l in out.splitlines()
                 if 'mCurrentFocus=' in l), None)
    if line is None or line.startswith('null'):
        return None
    activity = re.search(r'\s([A-Za-z][\w.]*)/', line)
    if activity:
        return activity.group(1)
    window = re.search(r'\s([^\s{}]+)\}', line)
    return window.group(1) if window else line.strip()


def normalize(value):
    return ' '.join(_BIDI.sub('', value or '').split())


def lines_of(node):
    """Visible lines of a node: Flutter merges a row's semantics into one
    content-desc with one line per part."""
    out = []
    for raw in (node.get('text') or '', node.get('content-desc') or ''):
        out.extend(normalize(part) for part in _BIDI.sub('', raw).splitlines())
    return [line for line in out if line]


def rect(node):
    match = _BOUNDS.fullmatch(node.get('bounds', ''))
    if not match:
        return None
    left, top, right, bottom = map(int, match.groups())
    return (left, top, right, bottom) if right > left and bottom > top else None


def area(r):
    return (r[2] - r[0]) * (r[3] - r[1])


class Ui:
    def __init__(self, adb, sleep=time.sleep, clock=time.monotonic):
        self.adb, self.sleep, self.clock = adb, sleep, clock
        self.parents = {}
        self.window = None

    def dump(self):
        raw = self.adb.run('exec-out', 'uiautomator', 'dump', '/dev/tty', timeout=30, check=False)
        start, end = raw.find('<hierarchy'), raw.rfind('</hierarchy>')
        if start < 0 or end < start or '<!DOCTYPE' in raw or '<!ENTITY' in raw:
            return []
        try:
            root = ET.fromstring(raw[start:end + len('</hierarchy>')])
        except ET.ParseError:
            return []
        nodes = [n for n in root.iter('node')]
        self.parents = {child: parent for parent in root.iter() for child in parent}
        self.window = next((rect(n) for n in nodes if rect(n)), None)
        return nodes

    @staticmethod
    def ours(nodes):
        return [n for n in nodes if n.get('package') == PACKAGE]

    def ensure_foreground(self, nodes=None):
        """Raise ForegroundStolen unless the app holds the foreground."""
        last = None
        for attempt in range(5):
            last = foreground_package(self.adb)
            top = next((n.get('package') for n in (nodes or []) if n.get('package')), PACKAGE)
            if last == PACKAGE and top == PACKAGE:
                return
            if attempt < 4:
                self.sleep(2)
                nodes = self.dump() if nodes else None
        raise ForegroundStolen(last or 'unknown')

    def find(self, nodes, label):
        wanted = normalize(label)
        hits = [n for n in self.ours(nodes)
                if wanted in lines_of(n) and n.get('visible-to-user', 'true') != 'false'
                and rect(n)]
        return min(hits, key=lambda n: area(rect(n))) if hits else None

    def wait_for_label(self, label, timeout=20, scroll=True):
        deadline, polls = self.clock() + timeout, 0
        while True:
            nodes = self.dump()
            node = self.find(nodes, label)
            if node is not None:
                return nodes, node
            if self.clock() >= deadline:
                raise CheckFailed(f'"{label}" not on screen')
            polls += 1
            if scroll and polls % 2 == 0 and polls <= 6 and nodes:
                self.swipe_up(nodes)
            else:
                self.sleep(1.5)

    def tap_node(self, nodes, node):
        self.ensure_foreground(nodes)
        left, top, right, bottom = rect(node)
        self.adb.run('shell', 'input', 'tap', (left + right) // 2, (top + bottom) // 2)
        self.sleep(1.5)

    def tap(self, label, timeout=20):
        nodes, node = self.wait_for_label(label, timeout)
        self.tap_node(nodes, node)

    def follow(self, path, timeout=20):
        for label in [p.strip() for p in path.split('>') if p.strip()]:
            self.tap(label, timeout)

    def swipe_up(self, nodes):
        if not self.window:
            return
        self.ensure_foreground(nodes)
        left, top, right, bottom = self.window
        x = (left + right) // 2
        self.adb.run('shell', 'input', 'swipe', x, top + (bottom - top) * 7 // 10,
                     x, top + (bottom - top) * 3 // 10, 300)
        self.sleep(1)

    def type_into_composer(self, text):
        if not _TYPABLE.fullmatch(text):
            raise CheckFailed('prompt has characters input text cannot carry')
        deadline = self.clock() + 20
        while True:
            nodes = self.dump()
            fields = [n for n in self.ours(nodes)
                      if 'EditText' in (n.get('class') or '') and rect(n)]
            if fields:
                break
            if self.clock() >= deadline:
                raise CheckFailed('no message field on screen')
            self.sleep(1.5)
        field = max(fields, key=lambda n: rect(n)[3])
        self.tap_node(nodes, field)
        self.ensure_foreground(self.dump())
        self.adb.run('shell', "input text '" + text.replace(' ', '%s') + "'", timeout=120)
        self.sleep(1)

    def visible_lines(self):
        return {line for node in self.ours(self.dump()) for line in lines_of(node)}

    def wait_for_text(self, matcher, timeout, ignore=frozenset()):
        """First new line of the app's nodes that matcher accepts, or None.
        Lines in ignore (on screen before the prompt was sent) never count."""
        deadline = self.clock() + timeout
        while True:
            for node in self.ours(self.dump()):
                for line in lines_of(node):
                    found = None if line in ignore else matcher(line)
                    if found:
                        return found
            if self.clock() >= deadline:
                return None
            self.sleep(3)

    def same_row(self, label_node, other):
        """True when other is label_node or sits in the same compact row."""
        if other is label_node:
            return True
        node = label_node
        for _ in range(4):
            node = self.parents.get(node)
            if node is None or rect(node) is None:
                return False
            if self.window and area(rect(node)) > area(self.window) * 0.4:
                return False
            if any(child is other for child in node.iter()):
                return True
        return False


class Smoke:
    def __init__(self, adb, ui, options, out=print, launch_wait=4):
        self.adb, self.ui, self.o, self.out = adb, ui, options, out
        self.launch_wait = launch_wait
        self.token = 'armsmoke' + secrets.token_hex(4)
        self.results = []
        self.chat_open = False
        self.files_made = False

    def report(self, name, ok, detail=''):
        self.results.append((name, ok))
        self.out(f'{"PASS" if ok else "FAIL"} {name}' + (f': {detail}' if detail else ''))

    def launch(self):
        self.adb.run('shell', 'am', 'start', '-n', ACTIVITY, timeout=30)
        self.ui.sleep(self.launch_wait)

    def ask(self, text, matcher):
        """Send a prompt; return what matcher found in a line that appeared after it."""
        before = self.ui.visible_lines()
        self.ui.type_into_composer(text)
        self.ui.tap(self.o.send_label)
        return self.ui.wait_for_text(matcher, self.o.reply_timeout, ignore=before)

    def check_phone_connected(self):
        self.launch()
        self.ui.follow(self.o.phone_path)
        _, label = self.ui.wait_for_label(self.o.phone_label, timeout=20)
        deadline = self.ui.clock() + 30
        while True:
            nodes = self.ui.dump()
            label = self.ui.find(nodes, self.o.phone_label)
            connected = [n for n in self.ui.ours(nodes) if self.o.connected_label in lines_of(n)]
            if label is not None and any(self.ui.same_row(label, n) for n in connected):
                return f'{self.o.phone_label}: {self.o.connected_label}'
            if self.ui.clock() >= deadline:
                raise CheckFailed(f'"{self.o.phone_label}" does not show '
                                  f'"{self.o.connected_label}"')
            self.ui.sleep(3)

    def check_new_chat(self):
        self.launch()
        self.ui.follow(self.o.new_chat_path)
        self.chat_open = True
        if not self.ask(f'Reply with just the word ok. Smoke {self.token}',
                        lambda line: line if _OK.fullmatch(line) else None):
            raise CheckFailed('no "ok" reply')
        return 'ok'

    def check_shell_arm(self):
        found = {}

        def seen(line):
            if _GIT.search(line):
                found['git'] = _GIT.search(line).group(0)
            if _ARCH.search(line):
                found['arch'] = _ARCH.search(line).group(1)
            return found if len(found) == 2 else None

        if not self.ask('Use the shell tool to run exactly: git --version; uname -m. '
                        'Then reply with only the output', seen):
            raise CheckFailed('expected "git version N.N" and an ARM machine name; saw '
                              + (', '.join(sorted(found)) or 'neither'))
        return f'{found["git"]}, {found["arch"]}'

    def check_tar(self):
        work = f'/tmp/{self.token}'
        self.files_made = True
        want = self.token.upper()
        if not self.ask(f'Use the shell tool to run exactly: mkdir -p {work} && cd {work} && '
                        f'echo {self.token} | tr a-z A-Z > a.txt && tar -czf a.tgz a.txt && '
                        'rm a.txt && tar -xzf a.tgz && cat a.txt. Then reply with only the output',
                        lambda line: want if want in line else None):
            raise CheckFailed(f'{want} did not come back from the tar round trip')
        return want

    def cleanup(self):
        if self.files_made:
            work = f'/tmp/{self.token}'
            if not self.ask(f'Use the shell tool to run exactly: rm -rf {work}; '
                            f'test -e {work} || echo removed | tr a-z A-Z. '
                            'Then reply with only the output',
                            lambda line: 'REMOVED' if re.search(r'\bREMOVED\b', line) else None):
                raise CheckFailed(f'{work} was not confirmed removed')
        self.ui.follow(self.o.delete_path)
        self.chat_open = False
        self.launch()
        if self.ui.wait_for_text(lambda line: line if self.token in line else None, 5):
            raise CheckFailed('the scratch conversation is still listed')
        return 'scratch conversation and files removed'

    def run_check(self, name, func):
        try:
            self.report(name, True, func())
            return True
        except CheckFailed as error:
            self.report(name, False, str(error))
            return False

    def run(self):
        try:
            self.run_check('phone_connected', self.check_phone_connected)
            if self.run_check('new_chat_reply_ok', self.check_new_chat):
                if self.run_check('shell_git_uname_arm', self.check_shell_arm):
                    self.run_check('tar_roundtrip_tmp', self.check_tar)
            if self.chat_open or self.files_made:
                self.run_check('cleanup', self.cleanup)
        except ForegroundStolen as error:
            self.out(f'REFUSED: foreground is {error}, not {PACKAGE}; no tap was sent to it')
            if self.chat_open or self.files_made:
                self.out(f'NOT RUN cleanup: delete the conversation with "{self.token}" and '
                         f'/tmp/{self.token} by hand')
            self.out('RESULT: FAIL')
            return EXIT_FOREGROUND
        ok = all(passed for _, passed in self.results)
        self.out(f'RESULT: {"PASS" if ok else "FAIL"}')
        return 0 if ok else EXIT_FAIL


def parse_args(argv):
    p = argparse.ArgumentParser(prog='tool/qa/phone_smoke.sh',
                                description='ARM phone smoke; see tool/qa/README.md')
    p.add_argument('serial')
    p.add_argument('apk')
    for key, value in DEFAULTS.items():
        p.add_argument('--' + key.replace('_', '-'), default=value,
                       help=f'default: {value!r} (">" separates taps)')
    p.add_argument('--reply-timeout', type=int, default=240)
    return p.parse_args(argv)


def preflight(adb):
    if adb.run('get-state', timeout=20, check=False).strip() != 'device':
        raise Refused('phone is not connected as an adb device')
    abi = adb.run('shell', 'getprop', 'ro.product.cpu.abi', timeout=20, check=False).strip()
    if not abi.startswith('arm'):
        raise Refused(f'not an ARM phone (cpu abi {abi or "unknown"})')


def main(argv=None, runner=subprocess.run, sleep=time.sleep, clock=time.monotonic, out=print):
    try:
        o = parse_args(argv)
        adb = Adb(o.serial, os.environ.get('ADB', 'adb'), runner)
        lock_path = Path(tempfile.gettempdir()) / f'phone-smoke-{o.serial.replace(":", "_")}.lock'
        with open(lock_path, 'w') as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError:
                raise Refused('another phone smoke is running on this phone') from None
            preflight(adb)
            digest = install_checked(adb, o.apk, os.environ.get('APKSIGNER', 'apksigner'), runner)
            out(f'PASS install_same_signer: {short(digest)}')
            return Smoke(adb, Ui(adb, sleep, clock), o, out).run()
    except Refused as error:
        out(f'REFUSED: {error}')
        return EXIT_REFUSED
    except CheckFailed as error:
        out(f'FAIL install_same_signer: {error}')
        out('RESULT: FAIL')
        return EXIT_FAIL


if __name__ == '__main__':
    sys.exit(main())
