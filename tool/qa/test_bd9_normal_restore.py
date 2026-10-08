"""Offline owner-APK restoration guards; no real adb or builds."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tool.qa import bd9_normal_restore as restore


SIGNER = '1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C'
PRIVATE = 'synthetic-private-provider-detail'


class NormalRestoreTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.apk = self.root / 'owner.apk'
        self.apk.write_bytes(b'normal-apk-fixture')
        self.receipt = self.root / 'proof' / 'normal-restore.json'
        self.commands = []
        self.clock = 0
        self.signer = SIGNER
        self.version = 2195
        self.package = restore.smoke.PACKAGE
        self.installed_version = 2195
        self.install_result = b'Success\n'
        self.launch_result = b'Status: ok\n'
        self.pid = b'1234\n'
        self.activity = True
        self.frame = True
        self.failure = None
        self.patchers = [
            patch.object(restore.smoke, 'android_tool', side_effect=lambda name: name),
            patch.object(restore.smoke, 'execute', side_effect=self.execute),
            patch.object(restore.time, 'monotonic', side_effect=lambda: self.clock),
            patch.object(restore.time, 'sleep', side_effect=self.sleep),
        ]
        for patcher in self.patchers:
            patcher.start()
            self.addCleanup(patcher.stop)

    def sleep(self, seconds):
        self.clock += seconds

    def execute(self, command, **kwargs):
        self.commands.append(command)
        if self.failure and self.failure(command):
            raise restore.smoke.SmokeFailure(PRIVATE)
        if command[0] == 'apksigner':
            return ('Signer #1 certificate SHA-256 digest: ' + self.signer + '\n' + PRIVATE).encode()
        if command[0] == 'aapt':
            return (f"package: name='{self.package}' versionCode='{self.version}'\n" + PRIVATE).encode()
        if command[3:4] == ['install']:
            return self.install_result
        if command[3:7] == ['shell', 'dumpsys', 'package', restore.smoke.PACKAGE]:
            return f'  versionCode={self.installed_version} minSdk=24\n{PRIVATE}'.encode()
        if command[3:6] == ['shell', 'am', 'start']:
            return self.launch_result
        if command[3:5] == ['shell', 'pidof']:
            return self.pid
        if command[3:6] == ['shell', 'dumpsys', 'activity']:
            component = restore.smoke.PACKAGE if self.activity else 'some.other.app'
            return f'mResumedActivity: ActivityRecord{{fixture {component}/.MainActivity}}\n{PRIVATE}'.encode()
        if command[3:4] == ['logcat']:
            return (restore.FIRST_FRAME if self.frame else b'no frame') + PRIVATE.encode()
        return b''

    def prepared(self):
        return restore.prepare_restore(self.apk, SIGNER, self.receipt)

    def report(self):
        content = self.receipt.read_text()
        self.assertNotIn(PRIVATE, content)
        return json.loads(content)

    def assert_failure(self, callback, category):
        with self.assertRaisesRegex(restore.smoke.SmokeFailure, '^' + category + '$'):
            callback()
        report = self.report()
        self.assertEqual(report['result'], 'FAIL')
        self.assertEqual(report['error'], category)

    def test_preflight_does_not_touch_device(self):
        self.prepared()
        self.assertEqual(self.report()['result'], 'READY')
        self.assertEqual([command[0] for command in self.commands], ['apksigner', 'aapt'])

    def test_missing_and_empty_apk_fail_before_any_commands(self):
        self.apk.unlink()
        self.assert_failure(self.prepared, 'normal_apk_unavailable')
        self.assertEqual(self.commands, [])
        self.apk.write_bytes(b'')
        self.assert_failure(self.prepared, 'normal_apk_unavailable')
        self.assertEqual(self.commands, [])

    def test_invalid_expected_signer_is_not_recorded(self):
        self.assert_failure(lambda: restore.prepare_restore(self.apk, PRIVATE, self.receipt),
                            'normal_signer_invalid')
        self.assertNotIn('signer_sha256', self.report())
        self.assertEqual(self.commands, [])

    def test_wrong_signer_blocks_session(self):
        self.signer = 'a' * 64
        self.assert_failure(self.prepared, 'normal_apk_signer_mismatch')

    def test_wrong_package_blocks_session(self):
        self.package = 'some.other.app'
        self.assert_failure(self.prepared, 'normal_apk_package_mismatch')

    def test_wrong_version_blocks_session(self):
        self.version = 2201
        self.assert_failure(self.prepared, 'normal_apk_version_mismatch')

    def test_changed_hash_blocks_install(self):
        callback = self.prepared()
        self.commands.clear()
        self.apk.write_bytes(b'changed-owner-apk')
        self.assert_failure(lambda: callback(['adb', '-s', 'emulator-5554']),
                            'normal_apk_changed')
        self.assertEqual(self.commands, [])

    def test_wrong_serial_or_prefix_blocks_install(self):
        callback = self.prepared()
        self.commands.clear()
        for adb in (['adb', '-s', 'other-device'], ['adb', '-d'],
                    ['adb', '-s', 'emulator-5554', 'extra'], ['', '-s', 'emulator-5554']):
            self.assert_failure(lambda: callback(adb), 'normal_restore_device_invalid')
        self.assertEqual(self.commands, [])

    def test_success_installs_owner_downgrade_and_proves_launch(self):
        callback = self.prepared()
        self.commands.clear()
        callback(['adb', '-s', 'emulator-5554'])
        self.assertEqual(self.commands[0], ['adb', '-s', 'emulator-5554',
                                          'install', '-r', '-d', str(self.apk)])
        self.assertTrue(all(command[:3] == ['adb', '-s', 'emulator-5554']
                            for command in self.commands))
        report = self.report()
        self.assertEqual(report['result'], 'PASS')
        self.assertEqual(report['version_code'], 2195)
        self.assertEqual(report['signer_sha256'], SIGNER)
        self.assertEqual(report['install'], 'PASS')
        self.assertTrue(report['process_alive'])
        self.assertTrue(report['activity_resumed'])
        self.assertTrue(report['first_frame'])
        self.assertEqual(self.clock, 0)

    def test_failed_install_output_is_private_and_never_launches(self):
        callback = self.prepared()
        self.commands.clear()
        self.install_result = ('Failure [' + PRIVATE + ']\n').encode()
        self.assert_failure(lambda: callback(['adb', '-s', 'emulator-5554']),
                            'normal_install_failed')
        self.assertEqual(len(self.commands), 1)

    def test_command_failure_uses_fixed_category(self):
        callback = self.prepared()
        self.failure = lambda command: command[3:4] == ['install']
        self.assert_failure(lambda: callback(['adb', '-s', 'emulator-5554']),
                            'normal_install_failed')

    def test_cleanup_is_best_effort_after_install(self):
        callback = self.prepared()
        self.commands.clear()
        self.failure = lambda command: command[3:5] == ['shell', 'rm']
        callback(['adb', '-s', 'emulator-5554'])
        self.assertEqual(self.report()['result'], 'PASS')
        self.assertEqual(self.report()['qa_image_cleanup'], 'FAIL')
        self.assertEqual(self.commands[0][3], 'install')
        self.assertEqual(self.commands[1][3:5], ['shell', 'rm'])

    def test_installed_version_must_be_owner_version(self):
        callback = self.prepared()
        self.installed_version = 2201
        self.assert_failure(lambda: callback(['adb', '-s', 'emulator-5554']),
                            'normal_installed_version_mismatch')

    def test_launch_command_must_succeed(self):
        callback = self.prepared()
        self.launch_result = PRIVATE.encode()
        self.assert_failure(lambda: callback(['adb', '-s', 'emulator-5554']),
                            'normal_launch_failed')

    def test_missing_pid_resumed_activity_or_frame_times_out(self):
        callback = self.prepared()
        for field, value, category in (
                ('pid', b'', 'normal_process_unavailable'),
                ('activity', False, 'normal_activity_not_resumed'),
                ('frame', False, 'normal_first_frame_unavailable')):
            previous = getattr(self, field)
            setattr(self, field, value)
            self.clock = 0
            self.assert_failure(lambda: callback(['adb', '-s', 'emulator-5554']), category)
            self.assertGreaterEqual(self.clock, restore.LAUNCH_BUDGET_SECONDS)
            self.assertLess(self.clock, restore.LAUNCH_BUDGET_SECONDS + 1)
            setattr(self, field, previous)

    def test_frame_can_arrive_on_a_later_bounded_poll(self):
        callback = self.prepared()
        self.frame = False
        original_sleep = self.sleep

        def later_frame(seconds):
            original_sleep(seconds)
            self.frame = True

        with patch.object(restore.time, 'sleep', side_effect=later_frame):
            callback(['adb', '-s', 'emulator-5554'])
        self.assertEqual(self.report()['result'], 'PASS')
        self.assertEqual(self.clock, restore.POLL_SECONDS)


if __name__ == '__main__':
    unittest.main()
