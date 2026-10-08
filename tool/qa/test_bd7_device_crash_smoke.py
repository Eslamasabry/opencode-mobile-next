"""Offline proof-driver ownership, privacy and rollback guards; no adb."""
import argparse
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from unittest.mock import Mock, patch

from tool.qa import bd7_device_crash_smoke as smoke


class CrashSmokeTest(unittest.TestCase):
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
