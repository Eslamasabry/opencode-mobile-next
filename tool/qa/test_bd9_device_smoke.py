"""Offline tests for fail-closed device receipts; never invoke adb."""
import argparse
import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tool.qa import bd9_device_smoke as smoke


def proof():
    lines = ['INSTRUMENTATION_STATUS: bd9NativeCheck=' + name + ':PASS' for name in smoke.CHECKS]
    lines += ['INSTRUMENTATION_RESULT: ' + key + '=' + value for key, value in smoke.RESULTS.items()]
    return '\n'.join(lines + ['INSTRUMENTATION_CODE: -1'])


class DeviceReceiptTest(unittest.TestCase):
    def test_complete_proof(self):
        result = smoke.parse_instrumentation(proof())
        self.assertEqual(result['native_checks'], list(smoke.CHECKS))
        self.assertEqual(result['flutter_tests'], 1)

    def test_partial_native_proof(self):
        for name in ('qa_password_file', 'stop_callback_order'):
            with self.assertRaises(smoke.SmokeFailure):
                smoke.parse_instrumentation(proof().replace('INSTRUMENTATION_STATUS: bd9NativeCheck=' + name + ':PASS', ''))

    def test_duplicate_native_proof(self):
        with self.assertRaises(smoke.SmokeFailure):
            smoke.parse_instrumentation(proof().replace('qa_password_file:PASS', 'auth_pipe_before_http:PASS'))

    def test_missing_or_failed_flutter_proof(self):
        for output in (proof().replace('bd9Flutter=PASS', 'bd9Flutter=FAIL'),
                       proof().replace('bd9FlutterTests=1', 'bd9FlutterTests=0'),
                       proof().replace('INSTRUMENTATION_RESULT: bd9Flutter=PASS', ''),
                       proof().replace('bd9FlutterTests=1', 'bd9FlutterTests=2')):
            with self.assertRaises(smoke.SmokeFailure):
                smoke.parse_instrumentation(output)

    def test_duplicate_or_unknown_result_refused(self):
        for extra in ('INSTRUMENTATION_RESULT: bd9Result=PASS', 'INSTRUMENTATION_RESULT: bd9Failure=secret'):
            with self.assertRaises(smoke.SmokeFailure):
                smoke.parse_instrumentation(proof() + '\n' + extra)

    def test_terminal_android_failure_refused(self):
        for code in ('0', '1', '', '-1\nINSTRUMENTATION_CODE: -1'):
            with self.assertRaises(smoke.SmokeFailure):
                smoke.parse_instrumentation(proof().replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_CODE: ' + code))

    def test_raw_error_and_provider_values_not_in_report(self):
        private = 'synthetic-provider-credential-do-not-export'
        result = smoke.parse_instrumentation('Dart error: ' + private + '\n' + proof())
        self.assertNotIn(private, str(result))

    def test_same_signer_required_before_install(self):
        with patch.object(smoke, 'android_tool', return_value='apksigner'), \
             patch.object(smoke, 'execute', return_value=b'Signer #1 certificate SHA-256 digest: ' + b'b' * 64):
            with self.assertRaisesRegex(smoke.SmokeFailure, 'apk_signer_mismatch'):
                smoke.verify_apk(Path('candidate.apk'), 'a' * 64)

    def test_test_manifest_runner_attributes_are_scoped(self):
        cert = b'Signer #1 certificate SHA-256 digest: ' + b'a' * 64
        # AGP's generated instrumentation manifest has no versionCode.
        badging = f"package: name='{smoke.PACKAGE}.test' versionCode=''".encode()
        tree = (f'  E: instrumentation (line=1)\n'
                f'    A: android:name(0x01010003)="{smoke.PACKAGE}.Bd9DeviceSmoke"\n'
                f'    A: android:targetPackage(0x01010021)="{smoke.PACKAGE}"\n'
                f'  E: application (line=2)\n'
                f'    A: android:name(0x01010003)="other.application.Name"\n').encode()
        with patch.object(smoke, 'android_tool', return_value='tool'), \
             patch.object(smoke, 'execute', side_effect=[cert, badging, tree]):
            smoke.verify_apk(Path('candidate.apk'), 'a' * 64, is_test=True)

    def test_version_and_runner_verified(self):
        cert = b'Signer #1 certificate SHA-256 digest: ' + b'a' * 64
        tree = (f'  E: instrumentation (line=1)\n'
                f'    A: android:name(0x01010003)="{smoke.PACKAGE}.PhoneEngineAcceptance"\n'
                f'    A: android:targetPackage(0x01010021)="{smoke.PACKAGE}"\n').encode()
        for badging, is_test, error in (
            (f"package: name='{smoke.PACKAGE}' versionCode='2187'".encode(), False, 'apk_version_mismatch'),
            (f"package: name='{smoke.PACKAGE}.test' versionCode='0'".encode(), True, 'apk_runner_mismatch'),
        ):
            with patch.object(smoke, 'android_tool', return_value='tool'), \
                 patch.object(smoke, 'execute', side_effect=[cert, badging, tree]):
                with self.assertRaisesRegex(smoke.SmokeFailure, error):
                    smoke.verify_apk(Path('candidate.apk'), 'a' * 64, is_test=is_test)

    def test_device_commands_are_locked_exact_serial_and_update_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / 'candidate.apk'
            apk.write_bytes(b'fixture')
            args = argparse.Namespace(output=root / 'proof', apk=apk, test_apk=root / 'test.apk',
                                      expected_signer='a' * 64, adb='adb')
            calls, held = [], []

            def execute(command, **kwargs):
                self.assertTrue(held, 'device_command_without_lock')
                self.assertEqual(command[:3], ['adb', '-s', 'emulator-5554'])
                self.assertNotIn('uninstall', command)
                calls.append(command)
                if command[3:] == ['get-state']:
                    return b'device\n'
                if command[3:5] == ['install', '-r']:
                    return b'Success'
                if command[3:6] == ['shell', 'am', 'instrument']:
                    return proof().encode()
                if command[3:5] == ['exec-out', 'cat']:
                    return b'\xff\xd8small-jpg\xff\xd9'
                return b''

            with patch.dict('os.environ', {'OC_EMULATOR_LOCK': str(root / 'device.lock')}), \
                 patch.object(smoke, 'verify_apk'), \
                 patch.object(smoke.fcntl, 'flock', side_effect=lambda *args: held.append(True)), \
                 patch.object(smoke, 'execute', side_effect=execute), \
                 contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(smoke.run_device(args), 0)
            self.assertEqual(sum(c[3:5] == ['install', '-r'] for c in calls), 2)
            instrument = next(c for c in calls if 'instrument' in c)
            self.assertEqual(instrument[-4:], ['-e', 'bd9Qa', 'true', smoke.RUNNER])
            self.assertTrue((args.output / 'conversations.jpg').is_file())

    def test_error_output_remains_categorical(self):
        with tempfile.TemporaryDirectory() as directory:
            args = argparse.Namespace(output=Path(directory), apk=Path('missing'), test_apk=Path('missing'),
                                      expected_signer='a' * 64, adb='adb')
            captured = io.StringIO()
            with patch.object(smoke, 'verify_apk', side_effect=smoke.SmokeFailure('apk_signer_mismatch')), \
                 patch.object(smoke, 'execute') as execute, contextlib.redirect_stdout(captured):
                self.assertEqual(smoke.run_device(args), 1)
                execute.assert_not_called()
            self.assertEqual(captured.getvalue(), 'BD9 FAIL stage=apk_verification\n')


if __name__ == '__main__':
    unittest.main()
