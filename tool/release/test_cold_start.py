import importlib.util
from pathlib import Path
import unittest
spec=importlib.util.spec_from_file_location('cold',Path(__file__).with_name('cold_start.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ColdStartTest(unittest.TestCase):
    def test_valid_total_time(self): self.assertEqual(m.parse_launch('Status: ok\nTotalTime: 92\nWaitTime: 100\n'),92)
    def test_android_randomized_install_path(self):
        path='package:/data/app/~~fixture==/io.github.app-random==/base.apk'
        self.assertEqual(m.installed_apk_path([path]),path.removeprefix('package:'))
        for paths in [[],[path,path],['package:/data/app/foo/base.apk;echo unsafe']]:
            with self.assertRaises(ValueError):m.installed_apk_path(paths)
    def test_errors_and_missing_measurement(self):
        for text in ['Status: timeout\nTotalTime: 92\n','Status: ok\nWaitTime: 80\n','Status: ok\nTotalTime: -1\n']:
            with self.assertRaises(ValueError):m.parse_launch(text)
if __name__=='__main__':unittest.main()
