import unittest
import importlib.util
import sys
import contextlib
import io
import json
import tempfile
from pathlib import Path
from unittest.mock import patch, Mock
from xml.etree.ElementTree import Element
from device_install import account_mask_boxes, closed_error_code
import device_install as subject


class ContinuationTests(unittest.TestCase):
    def run_failed_install(self, *, leftover=False, observation_fails=False, restore_fails=False):
        absent = {'leftovers': False, 'targetPids': [], 'allocatedBytes': 0,
                  'staging': [], 'lockPresent': False}
        ports = Mock()
        ports.available_storage_bytes.return_value = 900000000
        ports.target_inventory.return_value = dict(absent, leftovers=leftover)
        ports.install_if_absent.side_effect = RuntimeError('app_install_failed')
        ports.setup_snapshot.return_value = {'state': 'done', 'components': {}}
        d, p = Mock(), Mock()
        ports.p = p
        artifact = {'build': 2202, 'sourceRevision': '1' * 40}
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()), \
                patch.object(subject.run, 'verify_artifact'), \
                patch.object(subject.run, 'select_apk', side_effect=RuntimeError('app_restore_failed') if restore_fails else None), \
                patch.object(subject.run, 'verify_installed_apk', create=True,
                             side_effect=RuntimeError('installed_app_hash_mismatch') if observation_fails else None):
            code = subject.run_case('fx', artifact=artifact, output=Path(directory),
                                   ports_factory=lambda _: (d, p, ports, {}))
            result = json.loads((Path(directory) / 'fx-device.json').read_text())
        self.assertEqual(code, 1)  # Recovery never promotes the failed row.
        return result, p

    def test_failed_install_can_continue_only_after_fresh_quiescent_clean_observation(self):
        result, p = self.run_failed_install()
        self.assertEqual(result['errorCode'], 'app_install_failed')
        self.assertTrue(result['continuation']['confirmed'])
        self.assertTrue(result['continuation']['normalVerified'])
        self.assertTrue(result['continuation']['setupIdle'])
        self.assertTrue(result['continuation']['targetsAbsent'])
        self.assertGreaterEqual(p.require_idle_setup.call_count, 2)

    def test_leftover_payload_prevents_continuation_even_with_normal_apk(self):
        result, _ = self.run_failed_install(leftover=True)
        self.assertEqual(result['errorCode'], 'target_not_absent')
        self.assertFalse(result['continuation']['confirmed'])

    def test_failed_normal_observation_prevents_continuation(self):
        result, _ = self.run_failed_install(observation_fails=True)
        self.assertFalse(result['continuation']['confirmed'])

    def test_failed_restore_prevents_continuation(self):
        result, _ = self.run_failed_install(restore_fails=True)
        self.assertFalse(result['continuation']['confirmed'])

    def test_active_setup_or_incomplete_inventory_fails_closed(self):
        absent = {'leftovers': False, 'targetPids': [], 'allocatedBytes': 0,
                  'staging': [], 'lockPresent': False}
        for key, value in [('targetPids', [123]), ('allocatedBytes', 4096),
                           ('staging', ['pending']), ('lockPresent', True),
                           ('leftovers', None), ('activeSetup', True),
                           ('storage', 799999999)]:
            with self.subTest(key=key), patch.object(subject.run, 'verify_installed_apk'):
                ports = Mock()
                ports.target_inventory.return_value = dict(absent, **{key: value})
                ports.available_storage_bytes.return_value = value if key == 'storage' else 900000000
                if key == 'activeSetup':
                    ports.p.require_idle_setup.side_effect = RuntimeError('active')
                self.assertFalse(subject.confirm_continuation(ports, {})['confirmed'])

class Tests(unittest.TestCase):
    def test_masks_claude_and_signed_in_account_nodes_without_returning_copy(self):
        nodes=[Element('node',{'content-desc':'Claude Code\nSigned in as synthetic-account','bounds':'[10,20][100,80]'}),
               Element('node',{'text':'Signed in as second-account','bounds':'[30,90][300,140]'}),
               Element('node',{'text':'fx\nSign in needed','bounds':'[0,200][400,260]'})]
        self.assertEqual(account_mask_boxes(nodes),[(10,20,100,80),(30,90,300,140)])
        self.assertNotIn('account',repr(account_mask_boxes(nodes)))
    def test_account_without_safe_bounds_refuses_screenshot(self):
        with self.assertRaises(RuntimeError):
            account_mask_boxes([Element('node',{'text':'Signed in as synthetic-account','bounds':''})])

    def test_scrim_masks_background_when_modal_hides_account_semantics(self):
        nodes=[Element('node',{'text':'Scrim','bounds':'[0,0][1080,2400]'}),
               Element('node',{'content-desc':'Install fx','bounds':'[53,2164][1028,2295]'})]
        self.assertEqual(account_mask_boxes(nodes),[(0,0,1080,2400)])

    def test_unknown_native_error_is_not_exported_as_fixed_code(self):
        self.assertIsNone(closed_error_code(RuntimeError('synthetic_provider_key')))
        self.assertEqual(closed_error_code(RuntimeError('app_install_timeout')), 'app_install_timeout')

class NavigationPrivacyTests(unittest.TestCase):
    def test_navigation_timeout_uses_configured_masked_capture(self):
        path=Path(__file__).resolve().parents[1]/'fq_install/device.py'
        spec=importlib.util.spec_from_file_location('navigation_device_test',path)
        device=importlib.util.module_from_spec(spec);spec.loader.exec_module(device)
        capture=Mock()
        probe=Mock()
        with patch.dict(sys.modules,{'probe':probe}), patch.object(device,'adb'), \
             patch.object(device,'ui',return_value=[]), patch.object(device.time,'sleep'), \
             patch.object(device,'shot',capture):
            with self.assertRaisesRegex(RuntimeError,'Main app not ready'):
                device.launch_agents()
        capture.assert_called_once_with('navigation-not-ready')

if __name__=='__main__':unittest.main()
