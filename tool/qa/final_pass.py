#!/usr/bin/env python3
"""One reserved emulator session; offline plan unless --execute is explicit.

Existing drivers retain their own eligibility checks. Incompatible/missing
inputs produce blocked rows, never an APK downgrade or invented qualification.
"""
import argparse
from contextlib import ExitStack, contextmanager, redirect_stderr, redirect_stdout
from datetime import date
import fcntl
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile
from urllib.parse import quote
import uuid

if __package__ in (None, ''):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

ROOT = Path(__file__).resolve().parents[2]
LOCK = Path('/home/eslam/Storage/tmp/oc-emulator.lock')
NORMAL = Path('/home/eslam/Storage/tmp/oc-apk-share/oc-2199.apk')
PACKAGE = 'io.github.eslamasabry.opencode_mobile'
ROWS = ('ba-install', 'ba-removal', 'ba-storage-floor', 'bb5', 'fq3',
        'fq9-upgrade', 'fq9-background', 'bd7', 'fb1', 'demo')


class BatchError(Exception):
    pass


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    mode = p.add_mutually_exclusive_group()
    mode.add_argument('--execute', action='store_true')
    mode.add_argument('--dry-run', action='store_true', help='Print the plan only (default)')
    p.add_argument('--candidate-apk', type=Path)
    p.add_argument('--candidate-build', type=int)
    p.add_argument('--normal-apk', type=Path, default=NORMAL)
    p.add_argument('--inputs', type=Path, help='Private per-row JSON; see final-pass-runner.md')
    p.add_argument('--date', type=date.fromisoformat, default=date.today())
    return p


def plan(args):
    return {'mode': 'execute' if args.execute else 'dry-run', 'deviceTouched': False,
            'serial': 'emulator-5554', 'lock': str(LOCK), 'lockWaitSeconds': 3600,
            'rows': list(ROWS), 'backgroundCheckpointsSeconds': [300, 1800],
            'normalBuild': 2199, 'summary': f'docs/qa/final-pass-{args.date}/README.md',
            'requirements': ['reviewed_candidate_apk', 'matching_signer', 'compatible_driver_inputs',
                             'prepared_synthetic_fixtures', 'manual_demo_operator'],
            'qualification': 'FB1 checks a plan only; demo requires a separate privacy review.'}


def load_inputs(path):
    if path is None:
        return {}
    if path.is_symlink() or path.stat().st_size > 65536:
        raise BatchError('invalid_inputs')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise BatchError('invalid_inputs')
            result[key] = value
        return result
    try:
        value = json.loads(path.read_text(), object_pairs_hook=unique)
    except (OSError, ValueError):
        raise BatchError('invalid_inputs') from None
    if (type(value) is not dict or set(value) - set(ROWS)
            or any(type(row) is not dict for row in value.values())):
        raise BatchError('invalid_inputs')
    return value


@contextmanager
def reservation():
    import time
    fd = os.open(LOCK, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    try:
        deadline = time.monotonic() + 3600
        while True:
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise BatchError('lock_timeout') from None
                time.sleep(.25)
        yield fd
    finally:
        # Close releases this reservation only after all synchronous children end.
        os.close(fd)


def verify_apks(args):
    from tool.qa.fq9.ports import apk_identity
    from tool.qa.fq9.common import LOCAL_SIGNER
    if args.candidate_apk is None or args.candidate_build is None:
        raise BatchError('candidate_required')
    identities = []
    for path, build in ((args.candidate_apk, args.candidate_build), (args.normal_apk, 2199)):
        path = path.absolute()
        if any(p.is_symlink() for p in (path, *path.parents)) or not path.is_file():
            raise BatchError('artifact_unavailable')
        info = apk_identity(path)
        if info['build'] != build or info['signer'] != LOCAL_SIGNER:
            raise BatchError('artifact_identity_mismatch')
        identities.append(info)
    return {'candidate_sha256': identities[0]['sha256'], 'normal_sha256': identities[1]['sha256']}


class BorrowedLock:
    parent = LOCK.parent
    def __init__(self, context):
        self.context = context
    def open(self, *args, **kwargs):
        self.context.validate_fd(self.context.lock_fd)
        return os.fdopen(os.dup(self.context.lock_fd), 'a')


class BorrowedFcntl:
    LOCK_EX, LOCK_NB, LOCK_UN = fcntl.LOCK_EX, fcntl.LOCK_NB, fcntl.LOCK_UN
    def __init__(self, context):
        self.context = context
    def flock(self, handle, operation):
        fd = handle if isinstance(handle, int) else handle.fileno()
        self.context.validate_fd(fd)
        if operation not in (self.LOCK_EX, self.LOCK_EX | self.LOCK_NB, self.LOCK_UN):
            raise BatchError('invalid_driver_lock_operation')
        if operation != self.LOCK_UN:
            fcntl.flock(fd, self.LOCK_EX | self.LOCK_NB)
        # Driver release closes its borrowed handle; it cannot end the batch lease.


class Context:
    def __init__(self, root, output, args, fd, command=None):
        self.root, self.output = root, output
        self.candidate, self.candidate_build = args.candidate_apk.absolute(), args.candidate_build
        self.normal_apk, self.normal_build = args.normal_apk.absolute(), 2199
        self.lock_fd, self.run_id = fd, 'final-' + uuid.uuid4().hex[:12]
        self._command = command
    def validate_fd(self, fd):
        actual, expected = os.fstat(fd), os.stat(LOCK)
        if (actual.st_dev, actual.st_ino) != (expected.st_dev, expected.st_ino):
            raise BatchError('invalid_inherited_lock')
    def installed_build(self):
        result = self.command(['adb', '-s', 'emulator-5554', 'shell', 'dumpsys', 'package', PACKAGE], timeout=30)
        match = re.search(rb'versionCode=(\d+)', result.stdout) if result.returncode == 0 else None
        return int(match[1]) if match else None
    def borrowed_lock(self):
        return BorrowedLock(self)
    @contextmanager
    def adopt_lock(self, module):
        self.validate_fd(self.lock_fd)
        original_lock, original_fcntl = module.LOCK, module.fcntl
        module.LOCK, module.fcntl = self.borrowed_lock(), BorrowedFcntl(self)
        try:
            yield
        finally:
            module.LOCK, module.fcntl = original_lock, original_fcntl
    def capture(self, fn):
        # Do not publish raw output: drivers can handle private runtime state.
        with tempfile.TemporaryFile(mode='w+') as sink, redirect_stdout(sink), redirect_stderr(sink):
            return fn()
    def command(self, argv, timeout=180, stdin=None):
        if self._command:
            return self._command(argv, timeout=timeout, stdin=stdin)
        return subprocess.run(argv, cwd=self.root, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                              stdin=subprocess.DEVNULL if stdin is None else stdin,
                              pass_fds=(self.lock_fd,), timeout=timeout, check=False)


def dispatch(row, config, context):
    if row.startswith('ba-') or row == 'bb5':
        from tool.qa.final_pass_install import run
    elif row.startswith('fq'):
        from tool.qa.final_pass_protocols import run
    else:
        from tool.qa.final_pass_misc import run
    return run(row, config, context)


def outcome(row, status, reason, receipts=()):
    return {'row': row, 'status': status, 'reason': reason, 'receipts': list(receipts)}


def normalize(row, result):
    if (type(result) is not dict or result.get('status') not in ('pass', 'fail', 'blocked')
            or not re.fullmatch(r'[a-z0-9_]{1,100}', result.get('reason', ''))):
        return outcome(row, 'fail', 'invalid_driver_result')
    receipts = [str(p) for p in result.get('receipts', []) if Path(p).is_file()]
    value = outcome(row, result['status'], result['reason'], receipts)
    # A claimed pass needs current evidence, not only an exit code.
    if value['status'] == 'pass' and not receipts:
        return outcome(row, 'fail', 'receipt_missing')
    if result.get('data', {}).get('safe_to_continue') is False:
        value['safe_to_continue'] = False
    return value


def write_summary(output, results, identities):
    lines = ['# Final device pass', '', 'Device: `emulator-5554`. One shared lock reservation.', '',
             'These rows report driver evidence; plan checks and an unreviewed GIF do not certify a device.', '']
    lines += [f'- {key}: `{value}`' for key, value in identities.items()]
    lines += ['', '| Row | Status | Reason | Receipts |', '| --- | --- | --- | --- |']
    for result in results:
        links = []
        for i, item in enumerate(result['receipts'], 1):
            relative = os.path.relpath(item, output)
            links.append(f'[receipt {i}]({quote(relative, safe="/.-_")})')
        lines.append(f"| {result['row']} | {result['status']} | {result['reason']} | {' · '.join(links)} |")
    temporary = output / '.README.pending'
    temporary.write_text('\n'.join(lines) + '\n')
    temporary.replace(output / 'README.md')


def execute(args, *, root=ROOT, lock=reservation, command=None, verify=verify_apks, dispatch=dispatch):
    if args.candidate_apk is None or args.candidate_build is None:
        raise BatchError('candidate_required')
    configs = load_inputs(args.inputs)
    output = root / 'docs/qa' / f'final-pass-{args.date}'
    if output.exists():
        raise BatchError('output_already_exists')
    output.mkdir(parents=True)
    try:
        identities = verify(args)
    except Exception:
        write_summary(output, [outcome(row, 'blocked', 'artifact_preflight_failed') for row in ROWS], {})
        return 1
    results, interrupted = [], False
    with ExitStack() as stack:
        try:
            fd = stack.enter_context(lock())
        except Exception:
            write_summary(output, [outcome(row, 'blocked', 'lock_unavailable') for row in ROWS], identities)
            return 1
        context = Context(root, output, args, fd, command)
        device_safe, candidate_selected = True, False
        try:
            for row in ROWS:
                if not device_safe and row != 'fb1':
                    results.append(outcome(row, 'blocked', 'device_prerequisite_failed'))
                    write_summary(output, results, identities)
                    continue
                try:
                    # Some drivers restore their own normal APK. Re-establish
                    # the candidate before the next independent device row.
                    if row != 'fb1' and (not candidate_selected or context.installed_build() != context.candidate_build):
                        installed = context.command(['adb', '-s', 'emulator-5554', 'install', '-r', str(context.candidate)])
                        if installed.returncode:
                            device_safe = False
                            results.append(outcome(row, 'fail', 'candidate_install_failed'))
                            write_summary(output, results, identities)
                            continue
                        candidate_selected = True
                    result = normalize(row, dispatch(row, configs.get(row, {}), context))
                    results.append(result)
                    if result.get('safe_to_continue') is False:
                        device_safe = False
                except subprocess.TimeoutExpired:
                    device_safe = False
                    results.append(outcome(row, 'fail', 'driver_timeout'))
                except Exception:
                    results.append(outcome(row, 'fail', 'driver_failed'))
                write_summary(output, results, identities)
        except KeyboardInterrupt:
            interrupted = True
            done = {result['row'] for result in results}
            results += [outcome(row, 'blocked', 'interrupted') for row in ROWS if row not in done]
        finally:
            try:
                restored = context.command(['adb', '-s', 'emulator-5554', 'install', '-r', '-d', str(context.normal_apk)])
                restored_ok = restored.returncode == 0 and context.installed_build() == context.normal_build
                restore_receipt = output / 'normal-restore.json'
                restore_receipt.write_text(json.dumps({'state': 'pass' if restored_ok else 'fail',
                    'expectedBuild': context.normal_build, 'sha256': identities.get('normal_sha256'),
                    'installReturnedSuccess': restored.returncode == 0, 'versionVerified': restored_ok}, indent=2) + '\n')
                results.append(outcome('normal-restore', 'pass' if restored_ok else 'fail',
                                       'normal_restored' if restored_ok else 'normal_restore_failed', [str(restore_receipt)]))
            except Exception:
                results.append(outcome('normal-restore', 'fail', 'normal_restore_failed'))
            write_summary(output, results, identities)
    return 130 if interrupted else int(any(row['status'] != 'pass' for row in results))


def main(argv=None):
    args = parser().parse_args(argv)
    if not args.execute:
        print(json.dumps(plan(args), indent=2))
        return 0
    previous = signal.getsignal(signal.SIGTERM)
    def interrupted(_signal, _frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, interrupted)
    try:
        return execute(args)
    except Exception:
        print('Final pass could not start. Check candidate, inputs, lock and output directory.')
        return 1
    finally:
        signal.signal(signal.SIGTERM, previous)


if __name__ == '__main__':
    raise SystemExit(main())
