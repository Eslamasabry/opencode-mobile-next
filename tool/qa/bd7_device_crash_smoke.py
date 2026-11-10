#!/usr/bin/env python3
"""Real Android/JVM crash and input-dispatch ANR proof, emulator-5554 only.

No raw UI, OS descriptions, stack traces or private report contents are saved.
One lock covers candidate install, proof, private diagnostic rollback and normal
app restoration. No crash hook, synthetic record or product change is needed.
"""
import argparse
import fcntl
import hashlib
import json
import re
import shlex
import sys
import time
import uuid
from pathlib import Path

if __package__ in (None, ''):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tool.qa import bd9_device_smoke as shared
from tool.qa.bd9_normal_restore import prepare_restore
from tool.qa.bd7_exit_proof import parse_exit_history, parse_crash_ring, ProofFailure
from tool.qa.bd7_device_ui import Bd7Ui, Bd7UiFailure

PACKAGE = shared.PACKAGE
NORMAL_APK = Path('/home/eslam/Storage/tmp/oc-apk-share/oc-2196.apk')
FILES = f'/data/user/0/{PACKAGE}/files'
LOCK_PATH = Path('/home/eslam/Storage/tmp/oc-emulator.lock')
ARTIFACTS = (
    'crash-diagnostics-consent', 'crash-diagnostics.json',
    'crash-diagnostics.pending', 'native-last-crash.properties',
    'native-last-crash.properties.tmp', 'diagnostics/report_problem.json',
    'diagnostics/report_problem.pending',
)


class DeviceFailure(ValueError):
    """Fixed categories only."""


def verify_candidate(apk, signer):
    raw = shared.execute([shared.android_tool('apksigner'), 'verify',
                          '--print-certs', str(apk)])
    certs = re.findall(rb'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})', raw)
    if [cert.upper() for cert in certs] != [signer.upper().encode('ascii')]:
        raise DeviceFailure('candidate_signer_mismatch')
    raw = shared.execute([shared.android_tool('aapt'), 'dump', 'badging', str(apk)])
    match = re.search(rb"^package: name='([^']+)' versionCode='([0-9]+)'", raw, re.M)
    if match is None or match[1] != PACKAGE.encode() or match[2] != b'2197':
        raise DeviceFailure('candidate_identity_mismatch')
    return hashlib.sha256(apk.read_bytes()).hexdigest()


class DeviceSession:
    def __init__(self, adb, output):
        self.adb = [adb, '-s', shared.SERIAL]
        self.output = output
        self.locked = False
        self.suspended = None
        self.backup = FILES + '/bd7-qa-' + uuid.uuid4().hex
        self.backed_up = False
        self.retain_baseline = True
        self.ui = Bd7Ui(self.execute)

    def execute(self, suffix, *, timeout=20):
        if not self.locked:
            raise DeviceFailure('device_lock_required')
        try:
            return shared.execute(self.adb + suffix, timeout=timeout)
        except Exception:
            raise DeviceFailure('device_command_failed') from None

    def root(self, command):
        return self.execute(['shell', 'su', '0', 'sh', '-c', shlex.quote(command)])

    def storage(self):
        raw = self.execute(['shell', 'df', '-k', '/data'])
        lines = raw.splitlines()
        if len(lines) != 2:
            raise DeviceFailure('storage_check_invalid')
        fields = lines[1].split()
        if len(fields) != 6 or not fields[3].isdigit() or not fields[4].endswith(b'%'):
            raise DeviceFailure('storage_check_invalid')
        available, used = int(fields[3]), int(fields[4][:-1])
        if available < 256 * 1024 or used >= 95:
            raise DeviceFailure('storage_insufficient')
        return {'available_kib': available, 'used_percent': used}

    def identity(self):
        raw = self.execute(['shell', 'pidof', PACKAGE]).strip()
        if re.fullmatch(rb'[1-9][0-9]{0,9}', raw) is None:
            raise DeviceFailure('main_process_unavailable')
        pid = int(raw)
        name = self.execute(['shell', 'su', '0', 'cat', f'/proc/{pid}/cmdline'])
        if name.rstrip(b'\0') != PACKAGE.encode():
            raise DeviceFailure('main_process_identity_invalid')
        packages = self.execute(['shell', 'cmd', 'package', 'list', 'packages', '-U', PACKAGE])
        owners = re.findall(rb'(?m)^package:' + re.escape(PACKAGE.encode()) + rb' uid:([0-9]+)\s*$', packages)
        status = self.execute(['shell', 'su', '0', 'cat', f'/proc/{pid}/status'])
        uid = re.search(rb'(?m)^Uid:\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)\s*$', status)
        if len(owners) != 1 or uid is None or any(value != owners[0] for value in uid.groups()):
            raise DeviceFailure('main_process_identity_invalid')
        stat = self.execute(['shell', 'su', '0', 'cat', f'/proc/{pid}/stat'])
        fields = stat.rsplit(b') ', 1)[-1].split()
        if len(fields) < 20 or not fields[19].isdigit():
            raise DeviceFailure('main_process_identity_invalid')
        return pid, int(fields[19])

    def still_owned(self, identity):
        try:
            return self.identity() == identity
        except DeviceFailure:
            return False

    def signal(self, identity, number):
        if number not in (19, 18) or not self.still_owned(identity):
            raise DeviceFailure('signal_identity_invalid')
        self.execute(['shell', 'su', '0', 'kill', f'-{number}', str(identity[0])])

    def resume(self):
        if self.suspended is not None:
            if self.still_owned(self.suspended):
                self.signal(self.suspended, 18)
            self.suspended = None

    navigation_timeout_seconds = 15

    def navigate_current_page(self):
        if self.ui.find('Settings') is None:
            return False
        self.ui.navigate_report()
        return True

    def recover_navigation(self):
        # Specialized proof drivers may recover an exact known product page.
        pass

    def launch(self):
        self.execute(['shell', 'input', 'keyevent', '224'])
        self.execute(['shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity'])
        deadline = time.monotonic() + self.navigation_timeout_seconds
        while time.monotonic() < deadline:
            if self.navigate_current_page():
                return
            self.recover_navigation()
            time.sleep(.25)
        raise DeviceFailure('app_navigation_not_ready')

    def backup_diagnostics(self):
        # Quiesce only this app before snapshotting its diagnostic artifacts.
        self.execute(['shell', 'am', 'force-stop', PACKAGE])
        commands = [f'mkdir -m 700 {shlex.quote(self.backup)}',
                    f'test ! -L {shlex.quote(FILES + "/diagnostics")}']
        for index, name in enumerate(ARTIFACTS):
            path = shlex.quote(FILES + '/' + name)
            saved = shlex.quote(self.backup + '/' + str(index))
            commands += [f'test ! -L {path}',
                         f'if test -e {path}; then test -f {path} && '
                         f'test "$(stat -c %s {path})" -le 300000 && '
                         f'cp -p {path} {saved} && touch {saved}.present; fi']
        self.root(' && '.join(commands))
        self.backed_up = True

    def restore_diagnostics(self):
        self.resume()
        if not self.backed_up:
            return
        self.execute(['shell', 'am', 'force-stop', PACKAGE])
        commands = []
        for index, name in enumerate(ARTIFACTS):
            path = shlex.quote(FILES + '/' + name)
            saved = shlex.quote(self.backup + '/' + str(index))
            commands += [f'test ! -L {path}',
                         f'if test -f {saved}.present; then cp -p {saved} {path} '
                         f'&& cmp -s {saved} {path}; else rm -f {path} && test ! -e {path}; fi']
        self.root(' && '.join(commands))

    def consent(self):
        path = shlex.quote(FILES + '/crash-diagnostics-consent')
        raw = self.root(f'if test -f {path}; then cat {path}; else printf 0; fi').strip()
        if re.fullmatch(rb'[0-9]{1,16}', raw) is None:
            raise DeviceFailure('consent_invalid')
        return int(raw)

    def enable_consent(self):
        before = self.consent()
        # Keep a private rescue snapshot when restoring enabled consent can
        # import this real OS ANR again on the subsequent normal launch.
        self.retain_baseline = before > 0
        switch = self.ui.scroll_find('Save crash reports on this phone')
        if switch.get('enabled') != 'true':
            raise DeviceFailure('consent_unavailable')
        self.ui.screenshot(self.output / 'consent-before.jpg', section='Crash reports')
        if before == 0:
            self.ui.tap('Save crash reports on this phone', contains=True)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            epoch = self.consent()
            if epoch > 0:
                self.ui.screenshot(self.output / 'consent-on.jpg', section='Crash reports')
                return before, epoch
            time.sleep(.25)
        raise DeviceFailure('consent_not_enabled')

    def died(self, identity):
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if not self.still_owned(identity):
                return
            time.sleep(.25)
        raise DeviceFailure('process_did_not_exit')

    def proof(self, identity, reason, source, after, label, *, include_exit_ui=True):
        raw = self.execute(['shell', 'dumpsys', 'activity', 'exit-info', PACKAGE])
        exit_entry = parse_exit_history(raw, PACKAGE, identity[0], reason)
        native_exists = self.root('if test -f ' + shlex.quote(FILES + '/native-last-crash.properties') + '; then printf 1; else printf 0; fi').strip()
        if native_exists not in (b'0', b'1'):
            raise DeviceFailure('capture_status_invalid')
        unavailable = any("Crash reports aren't available right now. Restart the app and try again." in self.ui.text(node)
                          for node in self.ui.nodes())
        status = {'os_exit': exit_entry, 'native_category_file_exists': native_exists == b'1',
                  'capture_unavailable_visible': unavailable}
        (self.output / (source + '-capture-status.json')).write_text(json.dumps(status, indent=2) + '\n')
        try:
            raw = self.execute(['shell', 'su', '0', 'cat', FILES + '/crash-diagnostics.json'])
        except DeviceFailure:
            raise DeviceFailure('saved_crash_ring_unavailable') from None
        record = parse_crash_ring(raw, source, after)
        self.ui.scroll_find(label)
        self.ui.screenshot(self.output / (source + '-report.jpg'), section='Crash reports')
        self.ui.tap(label, contains=True)
        self.ui.screenshot(self.output / (source + '-preview.jpg'), section='preview')
        self.execute(['shell', 'input', 'keyevent', '4'])
        if include_exit_ui:
            self.ui.scroll_find('Recent app exits')
            self.ui.tap('App stopped unexpectedly', contains=True)
            if self.ui.details_number('Reason code') != reason:
                raise DeviceFailure('visible_exit_reason_mismatch')
            self.ui.screenshot(self.output / (source + '-exit.jpg'), section='Recent app exits')
        return {'os_exit': exit_entry, 'saved_report': record,
                'visible_report': True, 'visible_recent_exit': include_exit_ui}

    def crash(self):
        identity = self.identity()
        after = int(self.execute(['shell', 'date', '+%s%3N']).strip())
        self.execute(['shell', 'am', 'crash', '--user', '0', str(identity[0])])
        self.died(identity)
        try:
            close = self.ui.find('Close app')
            title = self.ui.find('OpenCode Mobile keeps stopping')
            if title is None:
                title = self.ui.find('OpenCode Mobile has stopped')
            if close is not None and title is not None and close.get('package') == 'android':
                self.ui.tap('Close app')
        except Bd7UiFailure:
            pass  # Some Android builds suppress the crash dialog entirely.
        self.launch()
        return self.proof(identity, 4, 'native', after, 'The app closed unexpectedly')

    def anr(self):
        x, y = self.ui.centre(self.ui.scroll_find('Recent app exits'))
        identity = self.identity()
        after = int(self.execute(['shell', 'date', '+%s%3N']).strip())
        self.suspended = identity
        self.signal(identity, 19)
        # Harmless tap in the already-open diagnostics surface; no task input.
        try:
            self.execute(['shell', 'input', 'tap', str(x), str(y)], timeout=12)
        except DeviceFailure:
            pass  # Input injection can time out while Android diagnoses ANR.
        deadline = time.monotonic() + 40
        found = False
        while time.monotonic() < deadline:
            try:
                node = self.ui.find('Close app')
            except Bd7UiFailure:
                node = None  # The stopped process can stall accessibility first.
            if node is not None and 'android' in node.get('package', ''):
                found = True
                break
            time.sleep(.25)
        if not found:
            raise DeviceFailure('anr_dialog_unavailable')
        self.ui.screenshot(self.output / 'anr-dialog.jpg', section='anr')
        self.ui.tap('Close app')
        self.died(identity)
        self.resume()
        self.launch()
        return self.proof(identity, 6, 'anr', after, 'The app stopped responding')


def run(args):
    args.output.mkdir(parents=True, exist_ok=True)
    report = {'device': shared.SERIAL, 'version_code': 2197, 'result': 'FAIL'}
    stage = 'apk_preflight'
    session = DeviceSession(args.adb, args.output)
    try:
        report['apk_sha256'] = verify_candidate(args.apk, args.expected_signer)
        restore = prepare_restore(NORMAL_APK, args.expected_signer,
                                  args.output / 'normal-restore.json')
        with LOCK_PATH.open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            session.locked = True
            try:
                stage = 'storage_check'
                report['storage_before'] = session.storage()
                stage = 'private_baseline'
                session.backup_diagnostics()
                stage = 'candidate_install'
                session.execute(['install', '-r', str(args.apk)], timeout=90)
                session.launch()
                stage = 'consent'
                before, epoch = session.enable_consent()
                report['original_consent_enabled'] = before > 0
                report['consent_enabled'] = epoch > 0
                stage = 'native_crash'
                report['native_crash'] = session.crash()
                stage = 'anr'
                report['anr'] = session.anr()
                report['result'] = 'PASS'
            finally:
                rollback_ok = False
                try:
                    session.restore_diagnostics()
                    rollback_ok = True
                    report['diagnostic_baseline_restore'] = 'PASS'
                except Exception:
                    report['diagnostic_baseline_restore'] = 'FAIL'
                try:
                    restore(session.adb)
                    report['normal_app_restore'] = 'PASS'
                    if rollback_ok and not session.retain_baseline:
                        session.root('rm -r ' + shlex.quote(session.backup))
                    else:
                        report['private_baseline_retained'] = True
                except Exception:
                    report['normal_app_restore'] = 'FAIL'
                if not rollback_ok or report['normal_app_restore'] != 'PASS':
                    report['result'] = 'FAIL'
                session.locked = False
    except Exception as error:
        # Arbitrary OS/UI/private-file exceptions never enter the receipt.
        report.update(result='FAIL', failure_stage=stage)
        if isinstance(error, (DeviceFailure, Bd7UiFailure, ProofFailure)):
            code = error.args[0] if error.args else None
            if isinstance(code, str) and re.fullmatch(r'[a-z_]{1,60}', code):
                report['failure_code'] = code
    report['stage'] = 'complete' if report['result'] == 'PASS' else stage
    (args.output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('BD7 ' + report['result'] + ' stage=' + report['stage'], flush=True)
    return 0 if report['result'] == 'PASS' else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=Path('/home/eslam/Storage/tmp/oc-apk-share/oc-2197.apk'))
    parser.add_argument('--expected-signer', required=True)
    parser.add_argument('--output', type=Path, default=Path('build/bd7-device-proof'))
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args()
    if re.fullmatch(r'[a-fA-F0-9]{64}', args.expected_signer) is None:
        parser.error('Expected certificate SHA-256 digest.')
    return run(args)


if __name__ == '__main__':
    raise SystemExit(main())
