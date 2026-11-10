#!/usr/bin/env python3
"""Run a bounded, credential-free release smoke on emulator-5554 only.

All device operations share one flock. Raw device/Flutter failure output is
never printed or saved: the report contains only validated fixed result tokens.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

PACKAGE = 'io.github.eslamasabry.opencode_mobile'
RUNNER = f'{PACKAGE}.test/{PACKAGE}.Bd9DeviceSmoke'
SERIAL = 'emulator-5554'
CHECKS = (
    'auth_pipe_before_http', 'private_failure_frames', 'qa_password_file',
    'authenticated_health_tiers', 'registered_pdf_identity',
    'receipt_before_protection', 'stop_callback_order',
)
RESULTS = {
    'bd9PhoneEngineChecks': '7', 'bd9PhoneEngine': 'PASS',
    'bd9FlutterTests': '1', 'bd9Flutter': 'PASS', 'bd9Result': 'PASS',
}
SCREENSHOT = f'/sdcard/Android/data/{PACKAGE}/files/bd9-conversations.jpg'
FAILURE_CODES = frozenset((
    'native_regressions', 'launch_activity', 'register_plugin', 'flutter_results',
    'conversation_not_visible', 'flutter_missing_plugin', 'flutter_platform_failure',
    'flutter_socket_failure', 'flutter_bad_state', 'flutter_expectation',
    'flutter_type_failure', 'flutter_null_failure', 'flutter_initialization_failure',
    'flutter_assertion', 'flutter_timeout', 'export_screenshot',
    'flutter_fixture', 'flutter_preferences', 'flutter_bootstrap',
    'flutter_conversation', 'flutter_screenshot', 'flutter_cleanup',
    'flutter_complete', 'flutter_initializing', 'flutter_no_tests',
    'flutter_multiple_tests',
))


class SmokeFailure(ValueError):
    """Only fixed, application-authored codes may reach the report."""


def parse_instrumentation(output):
    checks, results, codes = [], {}, []
    for line in output.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: bd9NativeCheck='):
            value = line.partition('=')[2]
            if len(checks) >= len(CHECKS) or value != CHECKS[len(checks)] + ':PASS':
                raise SmokeFailure('native_result_invalid')
            checks.append(CHECKS[len(checks)])
        elif line.startswith('INSTRUMENTATION_RESULT: bd9'):
            field, separator, value = line.removeprefix('INSTRUMENTATION_RESULT: ').partition('=')
            if not separator or field in results or RESULTS.get(field) != value:
                raise SmokeFailure('flutter_result_invalid')
            results[field] = value
        elif line.startswith('INSTRUMENTATION_CODE:'):
            codes.append(line.partition(':')[2].strip())
    if tuple(checks) != CHECKS or results != RESULTS or codes != ['-1']:
        raise SmokeFailure('smoke_proof_incomplete')
    return {'native_checks': checks, 'phone_engine': 'PASS', 'flutter_tests': 1,
            'flutter': 'PASS', 'result': 'PASS'}


def failure_diagnosis(output):
    """Recover only an ordered native prefix and one known failure category."""
    checks, failures = [], []
    valid_checks = True
    for line in output.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: bd9NativeCheck='):
            value = line.partition('=')[2]
            if len(checks) >= len(CHECKS) or value != CHECKS[len(checks)] + ':PASS':
                valid_checks = False
            elif valid_checks:
                checks.append(CHECKS[len(checks)])
        elif line.startswith('INSTRUMENTATION_RESULT: bd9Failure='):
            failures.append(line.partition('=')[2])
    diagnosis = {'native_checks': checks if valid_checks else []}
    if len(failures) == 1 and failures[0] in FAILURE_CODES:
        diagnosis['failure_code'] = failures[0]
    return diagnosis


def execute(command, *, timeout=30):
    try:
        result = subprocess.run(command, capture_output=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise SmokeFailure('command_unavailable_or_timed_out') from None
    if result.returncode:
        raise SmokeFailure('command_failed')
    return result.stdout


def android_tool(name):
    explicit = shutil.which(name)
    if explicit:
        return explicit
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or
               str(Path.home() / 'Android/Sdk'))
    candidates = sorted((sdk / 'build-tools').glob(f'*/{name}'))
    if not candidates:
        raise SmokeFailure('android_tool_unavailable')
    return str(candidates[-1])


def verify_apk(apk, expected, *, is_test=False):
    output = execute([android_tool('apksigner'), 'verify', '--print-certs', str(apk)])
    certificates = re.findall(rb'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})', output)
    if certificates != [expected.lower().encode()] and certificates != [expected.upper().encode()]:
        raise SmokeFailure('apk_signer_mismatch')
    badging = execute([android_tool('aapt'), 'dump', 'badging', str(apk)])
    package = re.search(rb"^package: name='([^']+)' versionCode='([0-9]*)'", badging, re.MULTILINE)
    wanted = PACKAGE + ('.test' if is_test else '')
    if package is None or package[1].decode() != wanted:
        raise SmokeFailure('apk_package_mismatch')
    if not is_test and package[2] != b'2201':
        raise SmokeFailure('apk_version_mismatch')
    if is_test:
        tree = execute([android_tool('aapt'), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml']).decode('utf-8', errors='replace')
        match = re.search(r'(?m)^( +)E: instrumentation[^\n]*\n', tree)
        attributes = {}
        if match:
            indentation = len(match[1])
            for line in tree[match.end():].splitlines():
                if line.strip() and len(line) - len(line.lstrip()) <= indentation:
                    break
                attr = re.search(r'A: android:(name|targetPackage)(?:\([^)]*\))?="([^"]+)"', line)
                if attr:
                    attributes[attr[1]] = attr[2]
        if attributes != {'name': PACKAGE + '.Bd9DeviceSmoke', 'targetPackage': PACKAGE}:
            raise SmokeFailure('apk_runner_mismatch')


def run_device(args, *, restore=None):
    """Optionally restore the local normal app before releasing the device lock.

    The trusted in-process callback receives the fixed adb command prefix. The
    caller must already hold any build lock needed by that callback; the CLI
    and CI workflow intentionally keep their existing candidate-only behavior.
    """
    args.output.mkdir(parents=True, exist_ok=True)
    stage = 'apk_verification'
    report = {'device': SERIAL, 'version_code': 2201, 'result': 'FAIL'}
    restore_failed = False
    try:
        verify_apk(args.apk, args.expected_signer)
        verify_apk(args.test_apk, args.expected_signer, is_test=True)
        lock = Path(os.environ.get('OC_EMULATOR_LOCK', '/home/eslam/Storage/tmp/oc-emulator.lock'))
        lock.parent.mkdir(parents=True, exist_ok=True)
        with lock.open('a') as handle:
            fcntl.flock(handle, fcntl.LOCK_EX)
            adb = [args.adb, '-s', SERIAL]
            try:
                stage = 'device_ready'
                if execute(adb + ['get-state']).strip() != b'device':
                    raise SmokeFailure('emulator_not_ready')
                stage = 'install_candidate'
                execute(adb + ['install', '-r', str(args.apk)], timeout=90)
                stage = 'install_android_test'
                execute(adb + ['install', '-r', str(args.test_apk)], timeout=60)
                stage = 'instrumentation'
                result = execute(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'bd9Qa', 'true', RUNNER], timeout=180)
                output = result.decode('utf-8', errors='replace')
                try:
                    report.update(parse_instrumentation(output))
                    report['automatic_first_conversation_load'] = 'PASS'
                except SmokeFailure:
                    report.update(failure_diagnosis(output))
                    raise
                stage = 'screenshot'
                image = execute(adb + ['exec-out', 'cat', SCREENSHOT])
                if not image.startswith(b'\xff\xd8') or not image.endswith(b'\xff\xd9') or len(image) > 200_000:
                    raise SmokeFailure('screenshot_invalid')
                (args.output / 'conversations.jpg').write_bytes(image)
                execute(adb + ['shell', 'rm', '-f', SCREENSHOT])
                report['apk_sha256'] = hashlib.sha256(args.apk.read_bytes()).hexdigest()
                report['signer_sha256'] = args.expected_signer.upper()
                report['stage'] = 'complete'
            finally:
                if restore is not None:
                    try:
                        restore(adb)
                        report['normal_app_restore'] = 'PASS'
                    except Exception:
                        # Callback errors may contain signing/device data.
                        restore_failed = True
                        report['normal_app_restore'] = 'FAIL'
    except SmokeFailure as failure:
        report.update(result='FAIL', stage=stage, error=str(failure))
    except OSError:
        report.update(result='FAIL', stage=stage, error='evidence_file_unavailable')
    if restore_failed:
        if report['result'] == 'PASS':
            report.update(stage='normal_app_restore', error='normal_restore_failed')
        report.update(result='FAIL', restore_error='normal_restore_failed')
        if 'stage' not in report:
            report['stage'] = stage
    (args.output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    for name in report.get('native_checks', []):
        print('PASS ' + name)
    print('BD9 ' + report['result'] + ' stage=' + report['stage'])
    return 0 if report['result'] == 'PASS' else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected-signer', required=True)
    parser.add_argument('--apk', type=Path, default=Path('build/app/outputs/flutter-apk/app-release.apk'))
    parser.add_argument('--test-apk', type=Path, default=Path('build/app/outputs/apk/androidTest/release/app-release-androidTest.apk'))
    parser.add_argument('--output', type=Path, default=Path('build/bd9-device-smoke'))
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args(argv)
    if not re.fullmatch(r'[0-9a-fA-F]{64}', args.expected_signer):
        parser.error('Expected signer must be a SHA-256 certificate digest.')
    return run_device(args)


if __name__ == '__main__':
    sys.exit(main())
