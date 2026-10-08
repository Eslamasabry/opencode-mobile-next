import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch

class Tests(unittest.TestCase):
    def test_read_only_observation_does_not_join_app_uid_process_inventory(self):
        device=Mock(PKG='qa.app')
        def adb(*args,**kwargs):
            if args[:4]==('shell','cmd','package','path'):return 'package:/data/app/qa/base.apk'
            if args[:4]==('shell','stat','-c','%u'):return '10217'
            return '{}'
        device.adb.side_effect=adb
        path=Path(__file__).resolve().parents[1]/'fq_install/probe.py'
        spec=importlib.util.spec_from_file_location('observation_probe_test',path)
        probe=importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules,{'device':device}):spec.loader.exec_module(probe)
        probe.probe("print('{}')",observation=True)
        command=device.adb.call_args.args[1]
        self.assertTrue(command.startswith('su 0 /system/bin/sh -c '))
        self.assertNotIn('su 10217 ',command)
        self.assertIn('--kill-on-exit',command)

    def test_observation_cannot_enter_an_account_profile(self):
        device=Mock(PKG='qa.app');device.adb.return_value='1'
        path=Path(__file__).resolve().parents[1]/'fq_install/probe.py'
        spec=importlib.util.spec_from_file_location('observation_profile_test',path)
        probe=importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules,{'device':device}):spec.loader.exec_module(probe)
        with self.assertRaises(ValueError):
            probe.probe("print('{}')",profile='private-profile',observation=True)
        device.adb.assert_not_called()

if __name__=='__main__':unittest.main()
