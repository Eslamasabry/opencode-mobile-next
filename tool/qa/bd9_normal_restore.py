"""Prepare the owner APK restoration callback for a locked BD9 device session.

Preparation never invokes adb. The caller must pass the callback to
bd9_device_smoke.run_device, which retains the emulator flock through restoration.
Command output remains in memory; receipts contain fixed fields and categories.
"""
import hashlib
import json
from pathlib import Path
import re
import time

from tool.qa import bd9_device_smoke as smoke

NORMAL_VERSION = 2195
LAUNCH_BUDGET_SECONDS = 10
POLL_SECONDS = 0.25
FIRST_FRAME = b'OCTRACE mark app.first_frame'


def _write_receipt(path, receipt):
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(receipt, indent=2) + '\n')
    except OSError:
        raise smoke.SmokeFailure('normal_restore_receipt_unavailable') from None


def _digest(apk):
    try:
        if not apk.is_file() or apk.stat().st_size == 0:
            raise smoke.SmokeFailure('normal_apk_unavailable')
        digest = hashlib.sha256()
        with apk.open('rb') as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b''):
                digest.update(chunk)
        return digest.hexdigest()
    except OSError:
        raise smoke.SmokeFailure('normal_apk_unavailable') from None


def _execute(command, category, *, timeout=30):
    try:
        return smoke.execute(command, timeout=timeout)
    except smoke.SmokeFailure:
        raise smoke.SmokeFailure(category) from None


def _verify_owner_apk(apk, signer):
    try:
        apksigner = smoke.android_tool('apksigner')
        aapt = smoke.android_tool('aapt')
    except smoke.SmokeFailure:
        raise smoke.SmokeFailure('normal_apk_tools_unavailable') from None
    certificates = re.findall(
        rb'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})',
        _execute([apksigner, 'verify', '--print-certs', str(apk)],
                 'normal_apk_verification_failed'))
    if [value.upper() for value in certificates] != [signer.encode('ascii')]:
        raise smoke.SmokeFailure('normal_apk_signer_mismatch')
    badging = _execute([aapt, 'dump', 'badging', str(apk)],
                       'normal_apk_verification_failed')
    package = re.search(rb"^package: name='([^']+)' versionCode='([0-9]*)'",
                        badging, re.MULTILINE)
    if package is None or package[1] != smoke.PACKAGE.encode('ascii'):
        raise smoke.SmokeFailure('normal_apk_package_mismatch')
    if package[2] != str(NORMAL_VERSION).encode('ascii'):
        raise smoke.SmokeFailure('normal_apk_version_mismatch')


def _launch_proof(adb):
    launch = _execute(adb + ['shell', 'am', 'start', '-W', '-n',
                             smoke.PACKAGE + '/.MainActivity'],
                      'normal_launch_failed', timeout=30)
    if b'Status: ok' not in launch:
        raise smoke.SmokeFailure('normal_launch_failed')
    deadline = time.monotonic() + LAUNCH_BUDGET_SECONDS
    failure = 'normal_process_unavailable'
    while time.monotonic() < deadline:
        try:
            pid = _execute(adb + ['shell', 'pidof', smoke.PACKAGE],
                           'normal_process_unavailable', timeout=3).strip()
            if re.fullmatch(rb'[1-9][0-9]{0,9}', pid) is None:
                raise smoke.SmokeFailure('normal_process_unavailable')
            activities = _execute(adb + ['shell', 'dumpsys', 'activity', 'activities'],
                                  'normal_activity_not_resumed', timeout=3)
            component = smoke.PACKAGE.encode('ascii') + b'/.'
            if not any(b'ResumedActivity' in line and
                       component + b'MainActivity' in line
                       for line in activities.splitlines()):
                raise smoke.SmokeFailure('normal_activity_not_resumed')
            logs = _execute(adb + ['logcat', '-d', '--pid', pid.decode('ascii'),
                                  '-s', 'flutter:I', '*:S'],
                            'normal_first_frame_unavailable', timeout=3)
            if FIRST_FRAME not in logs:
                raise smoke.SmokeFailure('normal_first_frame_unavailable')
            return {'process_alive': True, 'activity_resumed': True,
                    'first_frame': True}
        except smoke.SmokeFailure as error:
            failure = str(error)
        time.sleep(POLL_SECONDS)
    raise smoke.SmokeFailure(failure)


def prepare_restore(apk, expected_signer, receipt_path):
    """Validate the owner APK without a device session, then return restore(adb).

    If preparation fails, record a fixed failure and do not start the QA session.
    Only emulator-5554 is accepted. The callback never builds or copies an APK.
    """
    apk, receipt_path = Path(apk), Path(receipt_path)
    receipt = {'device': smoke.SERIAL, 'version_code': NORMAL_VERSION,
               'result': 'FAIL', 'stage': 'preflight'}
    try:
        if not isinstance(expected_signer, str) or re.fullmatch(
                r'[0-9a-fA-F]{64}', expected_signer) is None:
            raise smoke.SmokeFailure('normal_signer_invalid')
        signer = expected_signer.upper()
        receipt['signer_sha256'] = signer
        expected_digest = _digest(apk)
        _verify_owner_apk(apk, signer)
        if _digest(apk) != expected_digest:
            raise smoke.SmokeFailure('normal_apk_changed')
        receipt.update(apk_sha256=expected_digest, result='READY', stage='prepared')
    except smoke.SmokeFailure as error:
        receipt['error'] = str(error)
        _write_receipt(receipt_path, receipt)
        raise
    except Exception:
        receipt['error'] = 'normal_restore_unexpected_failure'
        _write_receipt(receipt_path, receipt)
        raise smoke.SmokeFailure('normal_restore_unexpected_failure') from None
    _write_receipt(receipt_path, receipt)

    def restore(adb):
        restored = dict(receipt, result='FAIL', stage='restore_preflight')
        try:
            if not isinstance(adb, (list, tuple)) or len(adb) != 3 or not isinstance(
                    adb[0], str) or not adb[0] or list(adb[1:]) != ['-s', smoke.SERIAL]:
                raise smoke.SmokeFailure('normal_restore_device_invalid')
            adb = list(adb)
            if _digest(apk) != expected_digest:
                raise smoke.SmokeFailure('normal_apk_changed')
            restored['stage'] = 'install_normal'
            installed = _execute(adb + ['install', '-r', '-d', str(apk)],
                                 'normal_install_failed', timeout=90)
            if re.search(rb'(?m)^Success\s*$', installed) is None:
                raise smoke.SmokeFailure('normal_install_failed')
            restored['install'] = 'PASS'
            # Synthetic image cleanup cannot prevent the normal install.
            try:
                _execute(adb + ['shell', 'rm', '-f', smoke.SCREENSHOT,
                                smoke.SCREENSHOT.removesuffix('.jpg') + '.png'],
                         'normal_image_cleanup_failed')
                restored['qa_image_cleanup'] = 'PASS'
            except smoke.SmokeFailure:
                restored['qa_image_cleanup'] = 'FAIL'
            restored['stage'] = 'installed_version'
            package = _execute(adb + ['shell', 'dumpsys', 'package', smoke.PACKAGE],
                               'normal_installed_version_unavailable')
            versions = re.findall(rb'(?m)^\s*versionCode=([0-9]+)\b', package)
            if versions != [str(NORMAL_VERSION).encode('ascii')]:
                raise smoke.SmokeFailure('normal_installed_version_mismatch')
            restored['stage'] = 'launch_normal'
            restored.update(_launch_proof(adb))
            restored.update(result='PASS', stage='complete')
        except smoke.SmokeFailure as error:
            restored['error'] = str(error)
            raise
        except Exception:
            restored['error'] = 'normal_restore_unexpected_failure'
            raise smoke.SmokeFailure('normal_restore_unexpected_failure') from None
        finally:
            _write_receipt(receipt_path, restored)

    return restore
