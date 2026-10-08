"""Offline proof-driver ownership, privacy and rollback guards; no adb."""
import argparse
import contextlib
import io
import json
import shlex
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from unittest.mock import Mock, patch

from tool.qa import bd7_device_crash_smoke as smoke


class CrashSmokeTest(unittest.TestCase):
    def test_candidate_accepts_only_2197_while_normal_restore_stays_2196(self):
        signer = 'A' * 64
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'candidate.apk'
            apk.write_bytes(b'public-apk-fixture')
            for version, accepted in (('2197', True), ('2196', False)):
                responses = [
                    ('Signer #1 certificate SHA-256 digest: ' + signer).encode(),
                    ("package: name='" + smoke.PACKAGE + "' versionCode='" + version + "'").encode(),
                ]
                with patch.object(smoke.shared, 'android_tool', side_effect=lambda name: name), \
                     patch.object(smoke.shared, 'execute', side_effect=responses):
                    if accepted:
                        self.assertEqual(len(smoke.verify_candidate(apk, signer)), 64)
                    else:
                        with self.assertRaisesRegex(smoke.DeviceFailure, '^candidate_identity_mismatch$'):
                            smoke.verify_candidate(apk, signer)
        self.assertEqual(smoke.NORMAL_APK.name, 'oc-2196.apk')

    def test_cold_launch_waits_for_settings_semantics_before_navigation(self):
        session = smoke.DeviceSession('adb', Path('.'))
        session.ui = Mock()
        ready = [False]
        def find(_):
            if session.ui.find.call_count == 1:
                return None
            ready[0] = True
            return ET.Element('node')
        def navigate():
            if not ready[0]:
                raise smoke.Bd7UiFailure('navigation_target_unavailable')
        session.ui.find.side_effect = find
        session.ui.navigate_report.side_effect = navigate
        with patch.object(session, 'execute'), \
             patch.object(smoke.time, 'monotonic', side_effect=[0, 1, 2]), \
             patch.object(smoke.time, 'sleep'):
            session.launch()
        self.assertTrue(ready[0])
        session.ui.navigate_report.assert_called_once()

    def test_missing_saved_ring_retains_validated_os_exit_and_fixed_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            session = smoke.DeviceSession('adb', Path(directory))
            session.ui = Mock()
            session.ui.text.side_effect = smoke.Bd7Ui.text
            session.ui.nodes.return_value = [ET.Element('node', {
                'content-desc': "Crash reports aren't available right now. Restart the app and try again."})]
            entry = {'pid': 1234, 'reason': 4, 'status': 0, 'exact_main': True}
            with patch.object(smoke, 'parse_exit_history', return_value=entry), \
                 patch.object(session, 'root', return_value=b'1'), \
                 patch.object(session, 'execute', side_effect=[b'private-dump', smoke.DeviceFailure('device_command_failed')]):
                with self.assertRaisesRegex(smoke.DeviceFailure, '^saved_crash_ring_unavailable$'):
                    session.proof((1234, 88), 4, 'native', 1001, 'The app closed unexpectedly')
            receipt = json.loads((Path(directory) / 'native-capture-status.json').read_text())
            self.assertEqual(receipt['os_exit'], entry)
            self.assertTrue(receipt['capture_unavailable_visible'])
            self.assertTrue(receipt['native_category_file_exists'])
            self.assertNotIn('private-dump', str(receipt))

    def test_anr_waits_through_transient_hung_ui_dump(self):
        session = smoke.DeviceSession('adb', Path('.'))
        session.ui = Mock()
        session.ui.centre.return_value = (10, 20)
        session.ui.find.side_effect = [smoke.Bd7UiFailure('ui_unavailable'),
                                       ET.Element('node', {'package': 'android'})]
        with patch.object(session, 'identity', return_value=(1234, 88)), \
             patch.object(session, 'execute', side_effect=[b'1001', b'']), \
             patch.object(session, 'signal'), patch.object(session, 'died'), \
             patch.object(session, 'resume'), patch.object(session, 'launch'), \
             patch.object(session, 'proof', return_value={'result': 'PASS'}), \
             patch.object(smoke.time, 'monotonic', side_effect=[0, 1, 2]), \
             patch.object(smoke.time, 'sleep'):
            self.assertEqual(session.anr(), {'result': 'PASS'})
        session.ui.tap.assert_called_once_with('Close app')

    def test_disabled_consent_refuses_crash_flow_before_tapping(self):
        session = smoke.DeviceSession('adb', Path('.'))
        session.ui = Mock()
        session.ui.scroll_find.return_value = ET.Element('node', {'enabled': 'false'})
        with patch.object(session, 'consent', side_effect=[0, 1234]):
            with self.assertRaisesRegex(smoke.DeviceFailure, '^consent_unavailable$'):
                session.enable_consent()
        session.ui.tap.assert_not_called()

    def test_private_script_is_one_argument_through_adb_shell(self):
        session = smoke.DeviceSession('adb', Path('.'))
        command = 'mkdir /private/safe && test ! -L /private/safe'
        with patch.object(session, 'execute') as execute:
            session.root(command)
        remote = shlex.split(' '.join(execute.call_args.args[0][1:]))
        self.assertEqual(remote, ['su', '0', 'sh', '-c', command])

    def test_missing_candidate_never_starts_a_device_session(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = argparse.Namespace(apk=root / 'missing.apk', expected_signer='a' * 64,
                                      adb='adb', output=root / 'proof')
            with patch.object(smoke, 'DeviceSession') as session, \
                 patch.object(smoke, 'verify_candidate', side_effect=ValueError('private-sentinel')), \
                 patch.object(smoke, 'prepare_restore') as restore, \
                 contextlib.redirect_stdout(io.StringIO()) as console:
                self.assertEqual(smoke.run(args), 1)
            session.return_value.execute.assert_not_called()
            restore.assert_not_called()
            report = json.loads((args.output / 'report.json').read_text())
            self.assertEqual(report['stage'], 'apk_preflight')
            self.assertNotIn('private-sentinel', str(report) + console.getvalue())

    def test_device_calls_require_session_lock(self):
        session = smoke.DeviceSession('adb', Path('.'))
        with patch.object(smoke.shared, 'execute') as execute:
            with self.assertRaisesRegex(smoke.DeviceFailure, '^device_lock_required$'):
                session.execute(['get-state'])
            execute.assert_not_called()

    def test_signals_reject_reused_or_foreign_identity(self):
        session = smoke.DeviceSession('adb', Path('.'))
        with patch.object(session, 'still_owned', return_value=False), \
             patch.object(session, 'execute') as execute:
            with self.assertRaisesRegex(smoke.DeviceFailure, '^signal_identity_invalid$'):
                session.signal((1234, 88), 19)
            execute.assert_not_called()

    def test_resume_never_signals_a_replacement_process(self):
        session = smoke.DeviceSession('adb', Path('.'))
        session.suspended = (1234, 88)
        with patch.object(session, 'still_owned', return_value=False), \
             patch.object(session, 'signal') as signal:
            session.resume()
            signal.assert_not_called()
            self.assertIsNone(session.suspended)

    def test_cmdline_alone_cannot_claim_the_app_identity(self):
        session = smoke.DeviceSession('adb', Path('.'))
        replies = [b'1234', smoke.PACKAGE.encode() + b'\0',
                   ('package:' + smoke.PACKAGE + ' uid:10123\n').encode(),
                   b'Uid:\t1000\t1000\t1000\t1000\n',
                   ('1234 (app) S ' + '0 ' * 18 + '88').encode()]
        with patch.object(session, 'execute', side_effect=replies):
            with self.assertRaisesRegex(smoke.DeviceFailure, '^main_process_identity_invalid$'):
                session.identity()

    def test_device_failures_do_not_expose_private_output(self):
        session = smoke.DeviceSession('adb', Path('.'))
        session.locked = True
        with patch.object(smoke.shared, 'execute', side_effect=ValueError('private-sentinel')):
            with self.assertRaisesRegex(smoke.DeviceFailure, '^device_command_failed$'):
                session.execute(['get-state'])

    def test_native_crash_dialog_leaf_is_closed_before_relaunch(self):
        session = smoke.DeviceSession('adb', Path('.'))
        session.ui = Mock()
        session.ui.find.side_effect = [ET.Element('node', {'package': 'android'}),
                                       ET.Element('node'), None]
        with patch.object(session, 'identity', return_value=(1234, 88)), \
             patch.object(session, 'execute', side_effect=[b'1001', b'']), \
             patch.object(session, 'died'), patch.object(session, 'launch'), \
             patch.object(session, 'proof', return_value={'result': 'PASS'}):
            session.crash()
        session.ui.tap.assert_called_once_with('Close app')

    def fixture(self, *, fail_at=None, normal_failed=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = argparse.Namespace(apk=root / '2197.apk', expected_signer='a' * 64,
                                      adb='adb', output=root / 'proof')
            order = []
            session = Mock()
            session.adb = ['adb', '-s', 'emulator-5554']
            session.backup = '/private/fixed-backup'
            session.retain_baseline = False
            session.storage.return_value = {'available_kib': 1_000_000, 'used_percent': 79}
            session.enable_consent.return_value = (0, 1234)
            session.crash.return_value = {'os_exit': {'reason': 4}}
            session.anr.return_value = {'os_exit': {'reason': 6}}
            for name in ('backup_diagnostics', 'restore_diagnostics', 'crash', 'anr'):
                original_result = getattr(session, name).return_value
                def action(*_, step=name, result=original_result):
                    order.append(step)
                    if fail_at == step:
                        raise ValueError('private-sentinel')
                    return result
                getattr(session, name).side_effect = action
            def restore(_):
                order.append('normal_restore')
                if normal_failed:
                    raise ValueError('private-sentinel')
            with patch.object(smoke, 'DeviceSession', return_value=session), \
                 patch.object(smoke, 'LOCK_PATH', root / 'emulator.lock'), \
                 patch.object(smoke, 'verify_candidate', return_value='a' * 64), \
                 patch.object(smoke, 'prepare_restore', return_value=restore), \
                 contextlib.redirect_stdout(io.StringIO()) as console:
                code = smoke.run(args)
            report = json.loads((args.output / 'report.json').read_text())
            self.assertNotIn('private-sentinel', str(report) + console.getvalue())
            self.assertFalse(session.locked)
            self.assertLess(order.index('restore_diagnostics'), order.index('normal_restore'))
            return code, report, session

    def test_complete_proof_rolls_back_before_normal_restore(self):
        code, report, session = self.fixture()
        self.assertEqual(code, 0)
        self.assertEqual(report['normal_app_restore'], 'PASS')
        self.assertEqual(report['diagnostic_baseline_restore'], 'PASS')
        session.root.assert_called_once()

    def test_failed_anr_still_rolls_back_and_restores_normal(self):
        code, report, _ = self.fixture(fail_at='anr')
        self.assertEqual(code, 1)
        self.assertEqual(report['failure_stage'], 'anr')
        self.assertEqual(report['normal_app_restore'], 'PASS')

    def test_failed_private_rollback_still_restores_normal_and_retains_backup(self):
        code, report, session = self.fixture(fail_at='restore_diagnostics')
        self.assertEqual(code, 1)
        self.assertEqual(report['normal_app_restore'], 'PASS')
        self.assertEqual(report['diagnostic_baseline_restore'], 'FAIL')
        session.root.assert_not_called()

    def test_failed_normal_restore_cannot_pass_or_delete_backup(self):
        code, report, session = self.fixture(normal_failed=True)
        self.assertEqual(code, 1)
        self.assertEqual(report['normal_app_restore'], 'FAIL')
        session.root.assert_not_called()
