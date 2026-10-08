#!/usr/bin/env python3
"""Qualify the private auth channel on emulator-5554 and restore owner2195.

Raw instrumentation/tool output stays in memory. There are no screenshots or
account labels; fixed states and account-label presence are the only proof.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

if not __package__:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from tool.qa import bd9_device_smoke as shared
from tool.qa.bd9_normal_restore import prepare_restore

RUNNER_NAME = shared.PACKAGE + '.Bb8DeviceSmoke'
RUNNER = shared.PACKAGE + '.test/' + RUNNER_NAME
NORMAL_APK = Path('/home/eslam/Storage/tmp/oc-apk-share/oc-2195.apk')
DEFAULT_PROFILE_ID = '1790839392073695'
PROFILE_PATTERN = r'[A-Za-z0-9_-]{1,80}'
OWNER_PREFIX = 'flutter.oc.phoneAgentOwner.'
MAX_PREFERENCES_BYTES = 8 * 1024 * 1024
RESULTS = {
    'bb8Claude': 'signedIn', 'bb8Fx': 'signedOut',
    'bb8FxLogout': 'signedOut', 'bb8FxAfterLogout': 'signedOut',
    'bb8FlutterTests': '1', 'bb8Flutter': 'PASS', 'bb8Result': 'PASS',
}
FAILURES = frozenset((
    'authorization', 'launch_activity', 'register_plugin', 'flutter_results',
    'receipt', 'flutter_initializing', 'flutter_profile', 'flutter_claude_probe',
    'flutter_fx_probe', 'flutter_fx_logout', 'flutter_fx_after_logout',
    'flutter_receipt', 'flutter_ready', 'flutter_timeout', 'cleanup',
))


def parse_instrumentation(output):
    values, codes = {}, []
    for line in output.splitlines():
        if line.startswith('INSTRUMENTATION_RESULT: bb8'):
            name, separator, value = line.removeprefix('INSTRUMENTATION_RESULT: ').partition('=')
            allowed = (value in ('present', 'absent') if name == 'bb8ClaudeAccountLabel'
                       else RESULTS.get(name) == value)
            if not separator or name in values or not allowed:
                raise shared.SmokeFailure('auth_proof_invalid')
            values[name] = value
        elif line.startswith('INSTRUMENTATION_CODE:'):
            codes.append(line.partition(':')[2].strip())
    if values.keys() != RESULTS.keys() | {'bb8ClaudeAccountLabel'} or codes != ['-1']:
        raise shared.SmokeFailure('auth_proof_incomplete')
    return {'claude': 'signedIn', 'claude_account_label': values['bb8ClaudeAccountLabel'],
            'fx': 'signedOut', 'fx_logout': 'signedOut', 'fx_after_logout': 'signedOut',
            'flutter_tests': 1, 'flutter': 'PASS', 'result': 'PASS'}


def failure_phase(output):
    values = [line.partition('=')[2] for line in output.splitlines()
              if line.startswith('INSTRUMENTATION_RESULT: bb8Failure=')]
    return values[0] if len(values) == 1 and values[0] in FAILURES else None


def verify_apk(apk, signer, version_code, *, is_test=False):
    certificates = re.findall(
        rb'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})',
        shared.execute([shared.android_tool('apksigner'), 'verify', '--print-certs', str(apk)]))
    if [value.upper() for value in certificates] != [signer.upper().encode()]:
        raise shared.SmokeFailure('apk_signer_mismatch')
    badging = shared.execute([shared.android_tool('aapt'), 'dump', 'badging', str(apk)])
    package = re.search(rb"^package: name='([^']+)' versionCode='([0-9]*)'", badging, re.MULTILINE)
    wanted = shared.PACKAGE + ('.test' if is_test else '')
    if package is None or package[1] != wanted.encode():
        raise shared.SmokeFailure('apk_package_mismatch')
    if not is_test:
        if package[2] != str(version_code).encode():
            raise shared.SmokeFailure('apk_version_mismatch')
        return
    tree = shared.execute([shared.android_tool('aapt'), 'dump', 'xmltree', str(apk),
                           'AndroidManifest.xml']).decode('utf-8', errors='replace')
    runners = []
    for match in re.finditer(r'(?m)^( +)E: instrumentation[^\n]*\n', tree):
        attributes = {}
        for line in tree[match.end():].splitlines():
            if line.strip() and len(line) - len(line.lstrip()) <= len(match[1]):
                break
            attribute = re.search(r'A: android:(name|targetPackage)(?:\([^)]*\))?="([^"]+)"', line)
            if attribute:
                attributes[attribute[1]] = attribute[2]
        if attributes.get('name') == RUNNER_NAME:
            runners.append(attributes)
    if runners != [{'name': RUNNER_NAME, 'targetPackage': shared.PACKAGE}]:
        raise shared.SmokeFailure('apk_runner_mismatch')


def confirm_auth_owner(adb, profile_id):
    """Confirm an existing owner privately, before replacing the installed app.

    Preferences may contain private values. Keep them only in memory, inspect
    owner mappings only, and never retain XML or include it in exceptions.
    """
    if not isinstance(profile_id, str) or re.fullmatch(PROFILE_PATTERN, profile_id) is None:
        raise shared.SmokeFailure('auth_owner_unconfirmed')
    home = f'/data/user/0/{shared.PACKAGE}/files/linux/ubuntu/home/oc/.oc-profiles/{profile_id}'
    preferences = f'/data/user/0/{shared.PACKAGE}/shared_prefs/FlutterSharedPreferences.xml'
    try:
        shared.execute(adb + ['shell', 'su', '0', 'test', '-d', home])
        raw = shared.execute(adb + ['shell', 'su', '0', 'cat', preferences])
        if not 0 < len(raw) <= MAX_PREFERENCES_BYTES or b'<!DOCTYPE' in raw or b'<!ENTITY' in raw:
            raise ValueError()
        document = ET.fromstring(raw)
        if document.tag != 'map':
            raise ValueError()
        owners = {}
        for entry in document:
            name = entry.get('name', '')
            if not name.startswith(OWNER_PREFIX):
                continue
            owner_profile = name.removeprefix(OWNER_PREFIX)
            if (entry.tag != 'string' or len(entry) != 0 or name in owners or
                    re.fullmatch(PROFILE_PATTERN, owner_profile) is None or
                    not isinstance(entry.text, str) or
                    re.fullmatch(PROFILE_PATTERN, entry.text) is None):
                raise ValueError()
            owners[name] = entry.text
        if profile_id not in owners.values():
            raise ValueError()
    except Exception:
        raise shared.SmokeFailure('auth_owner_unconfirmed') from None


def run_device(args):
    args.output.mkdir(parents=True, exist_ok=True)
    report = {'device': shared.SERIAL, 'version_code': args.version_code, 'result': 'FAIL'}
    stage = 'apk_verification'
    try:
        verify_apk(args.apk, args.expected_signer, args.version_code)
        verify_apk(args.test_apk, args.expected_signer, args.version_code, is_test=True)
        stage = 'normal_apk_verification'
        restore = prepare_restore(NORMAL_APK, args.expected_signer,
                                  args.output / 'normal-restore.json')
        lock = Path(os.environ.get('OC_EMULATOR_LOCK', '/home/eslam/Storage/tmp/oc-emulator.lock'))
        lock.parent.mkdir(parents=True, exist_ok=True)
        with lock.open('a') as handle:
            fcntl.flock(handle, fcntl.LOCK_EX)
            adb = [args.adb, '-s', shared.SERIAL]
            try:
                stage = 'device_ready'
                if shared.execute(adb + ['get-state']).strip() != b'device':
                    raise shared.SmokeFailure('emulator_not_ready')
                stage = 'auth_owner_preflight'
                confirm_auth_owner(adb, getattr(args, 'profile_id', DEFAULT_PROFILE_ID))
                report['auth_owner_confirmed'] = 'PASS'
                stage = 'install_candidate'
                shared.execute(adb + ['install', '-r', str(args.apk)], timeout=90)
                stage = 'install_android_test'
                shared.execute(adb + ['install', '-r', str(args.test_apk)], timeout=60)
                stage = 'instrumentation'
                raw = shared.execute(adb + ['shell', 'am', 'instrument', '-w', '-r',
                                          '-e', 'bb8Qa', 'true', RUNNER], timeout=130)
                output = raw.decode('utf-8', errors='replace')
                try:
                    report.update(parse_instrumentation(output))
                except shared.SmokeFailure:
                    phase = failure_phase(output)
                    if phase is not None:
                        report['failure_code'] = phase
                    raise
                report['apk_sha256'] = hashlib.sha256(args.apk.read_bytes()).hexdigest()
                report['signer_sha256'] = args.expected_signer.upper()
                report['stage'] = 'complete'
            finally:
                try:
                    restore(adb)
                    report['normal_app_restore'] = 'PASS'
                except Exception:
                    report.update(result='FAIL', normal_app_restore='FAIL',
                                  restore_error='normal_restore_failed')
                    if report.get('stage') == 'complete':
                        report.update(stage='normal_app_restore', error='normal_restore_failed')
    except shared.SmokeFailure as failure:
        report.update(result='FAIL', stage=stage, error=str(failure))
    except Exception:
        report.update(result='FAIL', stage=stage, error='smoke_unavailable')
    report.setdefault('stage', stage)
    (args.output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('BB8 ' + report['result'] + ' stage=' + report['stage'])
    return 0 if report['result'] == 'PASS' else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected-signer', required=True)
    parser.add_argument('--version-code', type=int, default=2201)
    parser.add_argument('--profile-id', default=DEFAULT_PROFILE_ID)
    parser.add_argument('--apk', type=Path, default=Path('build/app/outputs/flutter-apk/app-release.apk'))
    parser.add_argument('--test-apk', type=Path,
                        default=Path('build/app/outputs/apk/androidTest/release/app-release-androidTest.apk'))
    parser.add_argument('--output', type=Path, default=Path('build/bb8-agent-auth-smoke'))
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args(argv)
    if not re.fullmatch(r'[0-9a-fA-F]{64}', args.expected_signer) or args.version_code < 1:
        parser.error('Expected a certificate SHA-256 digest and positive version code.')
    if re.fullmatch(PROFILE_PATTERN, args.profile_id) is None:
        parser.error('Expected a valid existing phone-agent owner profile ID.')
    return run_device(args)


if __name__ == '__main__':
    sys.exit(main())
