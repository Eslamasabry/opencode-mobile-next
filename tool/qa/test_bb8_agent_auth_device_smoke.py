"""Offline BB8 evidence checks; mocked commands never access a device."""
import argparse
import contextlib
import io
import json
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import patch

from tool.qa import bb8_agent_auth_device_smoke as smoke


def proof():
    values = dict(smoke.RESULTS, bb8ClaudeAccountLabel='present')
    return '\n'.join(['INSTRUMENTATION_RESULT: ' + key + '=' + value
                      for key, value in values.items()] + ['INSTRUMENTATION_CODE: -1'])


def preferences(owner=smoke.DEFAULT_PROFILE_ID):
    return (f'<map><string name="flutter.oc.phoneAgentOwner.main">{owner}</string>'
            '<string name="private-unrelated-setting">private-pref-sentinel</string>'
            '</map>').encode()


class AuthSmokeTest(unittest.TestCase):
    def test_qa_flag_selects_bb8_runner_and_retains_other_test_routes(self):
        root = Path(__file__).resolve().parents[2]
        gradle = (root / 'android/app/build.gradle.kts').read_text()
        self.assertRegex(gradle, r'val\s+ocBb8Smoke\s*=\s*'
                         r'\(project.findProperty\("ocBb8Smoke"\)\s+as\s+String\?\)\s*==\s*"true"')
        self.assertRegex(gradle, r'testInstrumentationRunner\s*=\s*'
                         r'"io\.github\.eslamasabry\.opencode_mobile\."\s*\+')
        self.assertRegex(gradle, r'if\s*\(ocBb8Smoke\)\s*"Bb8DeviceSmoke"\s*else\s*'
                         r'if\s*\(ocBd9Smoke\)\s*"Bd9DeviceSmoke"\s*else\s*"PhoneEngineAcceptance"')
        self.assertRegex(gradle, r'if\s*\(ocBd9Smoke\)\s*'
                         r'add\("releaseImplementation",\s*project\(":integration_test"\)\)')
        guard = re.search(r'if\s*\(([^\n]*ocBd9Smoke[^\n]*)\)\s*'
                          r'proguardFiles\("phone-engine-instrumentation\.pro"\)', gradle)
        self.assertIsNotNone(guard, 'QA embedding ABI must keep its existing R8 protection')
        self.assertIn('ocPreview', guard[1])
        self.assertIn('ocStableEngineQa', guard[1])

    def test_packaging_preflight_failure_leaves_device_and_normal_app_untouched(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = argparse.Namespace(apk=root / 'qa.apk', test_apk=root / 'test.apk',
                                      output=root / 'proof', adb='adb',
                                      expected_signer='a' * 64, version_code=2201,
                                      profile_id=smoke.DEFAULT_PROFILE_ID)
            with patch.object(smoke, 'verify_apk', side_effect=[None,
                              smoke.shared.SmokeFailure('apk_runner_mismatch')]), \
                 patch.object(smoke, 'prepare_restore') as prepare, \
                 patch.object(smoke.shared, 'execute') as execute, \
                 patch.object(smoke.fcntl, 'flock') as flock, \
                 contextlib.redirect_stdout(io.StringIO()):
                code = smoke.run_device(args)
            self.assertEqual(code, 1)
            prepare.assert_not_called()
            execute.assert_not_called()
            flock.assert_not_called()
            report = json.loads((args.output / 'report.json').read_text())
            self.assertEqual(report['stage'], 'apk_verification')
            self.assertEqual(report['error'], 'apk_runner_mismatch')
            self.assertNotIn('normal_app_restore', report)

    def test_complete_proof_exports_presence_without_account_data(self):
        result = smoke.parse_instrumentation('private-synthetic-account\n' + proof())
        self.assertEqual(result['claude_account_label'], 'present')
        self.assertEqual(result['fx_logout'], 'signedOut')
        self.assertNotIn('private-synthetic-account', str(result))

    def test_partial_duplicate_failed_or_unknown_proof_is_rejected(self):
        for candidate in (
            proof().replace('bb8FxAfterLogout=signedOut', 'bb8FxAfterLogout=signedIn'),
            proof().replace('INSTRUMENTATION_RESULT: bb8FxLogout=signedOut', ''),
            proof() + '\nINSTRUMENTATION_RESULT: bb8Fx=signedOut',
            proof() + '\nINSTRUMENTATION_RESULT: bb8Account=private-synthetic-account',
            proof().replace('bb8ClaudeAccountLabel=present', 'bb8ClaudeAccountLabel=private-account'),
            proof().replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_CODE: 0'),
        ):
            with self.assertRaises(smoke.shared.SmokeFailure):
                smoke.parse_instrumentation(candidate)

    def test_failure_phase_only_allows_fixed_values(self):
        self.assertEqual(smoke.failure_phase(
            'INSTRUMENTATION_RESULT: bb8Failure=flutter_fx_logout'), 'flutter_fx_logout')
        self.assertIsNone(smoke.failure_phase(
            'INSTRUMENTATION_RESULT: bb8Failure=private-account'))
        self.assertIsNone(smoke.failure_phase(
            'INSTRUMENTATION_RESULT: bb8Failure=flutter_fx_probe\n'
            'INSTRUMENTATION_RESULT: bb8Failure=flutter_fx_probe'))

    def test_readiness_timeout_and_cleanup_failures_are_fixed_and_never_pass(self):
        for phase in ('flutter_ready', 'flutter_timeout', 'cleanup'):
            output = 'INSTRUMENTATION_RESULT: bb8Failure=' + phase
            self.assertEqual(smoke.failure_phase(output), phase)
            with self.assertRaises(smoke.shared.SmokeFailure):
                smoke.parse_instrumentation(output)
        self.assertIsNone(smoke.failure_phase(
            'INSTRUMENTATION_RESULT: bb8Failure=flutter_timeout private-synthetic-account'))

    def run_fixture(self, *, failed_smoke=False, failed_restore=False,
                    owner_xml=None, home_missing=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / 'qa.apk'
            apk.write_bytes(b'fixture')
            args = argparse.Namespace(apk=apk, test_apk=root / 'test.apk',
                                      output=root / 'proof', adb='adb',
                                      expected_signer='a' * 64, version_code=2201,
                                      profile_id=smoke.DEFAULT_PROFILE_ID)
            lock = root / 'emulator.lock'
            restored, commands = [], []

            def restore(adb):
                self.assertEqual(adb, ['adb', '-s', 'emulator-5554'])
                with lock.open('a') as probe:
                    with self.assertRaises(BlockingIOError):
                        smoke.fcntl.flock(probe, smoke.fcntl.LOCK_EX | smoke.fcntl.LOCK_NB)
                restored.append(True)
                if failed_restore:
                    raise RuntimeError('private-restoration-detail')

            def execute(command, **kwargs):
                self.assertEqual(command[:3], ['adb', '-s', 'emulator-5554'])
                self.assertNotIn('uninstall', command)
                with lock.open('a') as probe:
                    with self.assertRaises(BlockingIOError):
                        smoke.fcntl.flock(probe, smoke.fcntl.LOCK_EX | smoke.fcntl.LOCK_NB)
                commands.append(command)
                if command[3:] == ['get-state']:
                    return b'device'
                if command[3:8] == ['shell', 'su', '0', 'test', '-d']:
                    self.assertEqual(command[8],
                                     f'/data/user/0/{smoke.shared.PACKAGE}/files/linux/ubuntu/'
                                     f'home/oc/.oc-profiles/{smoke.DEFAULT_PROFILE_ID}')
                    if home_missing:
                        raise smoke.shared.SmokeFailure('command_failed')
                    return b''
                if command[3:7] == ['shell', 'su', '0', 'cat']:
                    self.assertEqual(command[7],
                                     f'/data/user/0/{smoke.shared.PACKAGE}/shared_prefs/'
                                     'FlutterSharedPreferences.xml')
                    return preferences() if owner_xml is None else owner_xml
                if 'instrument' in command:
                    if failed_smoke:
                        return ('private-synthetic-account\n'
                                'INSTRUMENTATION_RESULT: bb8Failure=flutter_fx_probe\n'
                                'INSTRUMENTATION_CODE: 0').encode()
                    return proof().encode()
                return b'Success'

            with patch.dict('os.environ', {'OC_EMULATOR_LOCK': str(lock)}), \
                 patch.object(smoke, 'verify_apk'), \
                 patch.object(smoke, 'prepare_restore', return_value=restore) as prepare, \
                 patch.object(smoke.shared, 'execute', side_effect=execute), \
                 contextlib.redirect_stdout(io.StringIO()) as captured:
                code = smoke.run_device(args)
            prepare.assert_called_once_with(smoke.NORMAL_APK, args.expected_signer,
                                            args.output / 'normal-restore.json')
            self.assertEqual(restored, [True])
            report = json.loads((args.output / 'report.json').read_text())
            self.assertNotIn('private-synthetic-account', str(report) + captured.getvalue())
            self.assertNotIn('private-restoration-detail', str(report) + captured.getvalue())
            self.assertNotIn('private-pref-sentinel', str(report) + captured.getvalue())
            if report.get('error') == 'auth_owner_unconfirmed':
                self.assertFalse(any(command[3] == 'install' for command in commands))
                self.assertFalse(any('instrument' in command for command in commands))
            else:
                home_check = next(index for index, command in enumerate(commands)
                                  if command[3:8] == ['shell', 'su', '0', 'test', '-d'])
                owner_check = next(index for index, command in enumerate(commands)
                                   if command[3:7] == ['shell', 'su', '0', 'cat'])
                install = next(index for index, command in enumerate(commands)
                               if command[3] == 'install')
                self.assertLess(home_check, owner_check)
                self.assertLess(owner_check, install)
            return code, report

    def test_success_restores_inside_same_flock(self):
        code, report = self.run_fixture()
        self.assertEqual(code, 0)
        self.assertEqual(report['normal_app_restore'], 'PASS')
        self.assertEqual(report['auth_owner_confirmed'], 'PASS')

    def test_missing_profile_home_refuses_candidate_install_and_still_restores(self):
        code, report = self.run_fixture(home_missing=True)
        self.assertEqual(code, 1)
        self.assertEqual(report['error'], 'auth_owner_unconfirmed')
        self.assertEqual(report['stage'], 'auth_owner_preflight')
        self.assertEqual(report['normal_app_restore'], 'PASS')

    def test_existing_home_without_matching_owner_refuses_candidate_install(self):
        code, report = self.run_fixture(owner_xml=preferences('another-owner'))
        self.assertEqual(code, 1)
        self.assertEqual(report['error'], 'auth_owner_unconfirmed')
        self.assertEqual(report['normal_app_restore'], 'PASS')

    def test_malformed_or_ambiguous_private_preferences_refuse_install(self):
        for xml in (
            b'<map><private-pref-sentinel>', b'', b'<list/>',
            b'<map><string name="oc.phoneAgentOwner.main">1790839392073695</string></map>',
            b'<map><int name="flutter.oc.phoneAgentOwner.main" value="1790839392073695"/></map>',
            b'<map><string name="flutter.oc.phoneAgentOwner.main">1790839392073695</string>'
            b'<string name="flutter.oc.phoneAgentOwner.main">another-owner</string></map>',
            b'<!DOCTYPE map [<!ENTITY private "private-pref-sentinel">]><map/>',
        ):
            code, report = self.run_fixture(owner_xml=xml)
            self.assertEqual(code, 1)
            self.assertEqual(report['error'], 'auth_owner_unconfirmed')
            self.assertEqual(report['normal_app_restore'], 'PASS')

    def test_invalid_profile_ids_do_not_form_device_paths(self):
        for profile in ('', '../owner', 'owner/name', 'a' * 81, 'owner\n', None):
            with patch.object(smoke.shared, 'execute') as execute:
                with self.assertRaisesRegex(smoke.shared.SmokeFailure, '^auth_owner_unconfirmed$'):
                    smoke.confirm_auth_owner(['adb', '-s', 'emulator-5554'], profile)
            execute.assert_not_called()

    def test_owner_preflight_accepts_existing_alias_mapping_without_mutation(self):
        adb = ['adb', '-s', 'emulator-5554']
        with patch.object(smoke.shared, 'execute', side_effect=[b'', preferences()]) as execute:
            smoke.confirm_auth_owner(adb, smoke.DEFAULT_PROFILE_ID)
        self.assertEqual(execute.call_count, 2)
        self.assertEqual(execute.call_args_list[0].args[0][3:8],
                         ['shell', 'su', '0', 'test', '-d'])
        self.assertEqual(execute.call_args_list[1].args[0][3:7], ['shell', 'su', '0', 'cat'])

    def test_failed_probe_restores_inside_same_flock(self):
        code, report = self.run_fixture(failed_smoke=True)
        self.assertEqual(code, 1)
        self.assertEqual(report['normal_app_restore'], 'PASS')
        self.assertEqual(report['failure_code'], 'flutter_fx_probe')

    def test_failed_restore_cannot_leave_passing_proof(self):
        code, report = self.run_fixture(failed_restore=True)
        self.assertEqual(code, 1)
        self.assertEqual(report['stage'], 'normal_app_restore')
        self.assertEqual(report['normal_app_restore'], 'FAIL')


if __name__ == '__main__':
    unittest.main()
