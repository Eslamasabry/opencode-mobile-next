import unittest
import importlib.util
import sys
from pathlib import Path
from unittest.mock import patch, Mock
from xml.etree.ElementTree import Element
from device_2198 import account_mask_boxes, closed_error_code

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
