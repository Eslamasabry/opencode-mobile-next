import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('apk_size', Path(__file__).with_name('check_apk_size.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class ApkSizeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.apk = Path(self.tmp.name) / 'fixture.apk'
        self.apk.write_bytes(b'fixture')
        self.release = {'tag_name':'v1.0.0+1','draft':False,'prerelease':False,'assets':[{'name':'opencode-mobile-1.0.0+1.apk','size':1}]}
    def tearDown(self): self.tmp.cleanup()
    def check(self, budget=5): return m.measure(self.apk,self.release,'v1.0.0+1',budget)
    def test_growth_rejected(self): self.assertFalse(self.check(0)['passed'])
    def test_growth_accepted_and_hash_recorded(self):
        x=self.check(); self.assertTrue(x['passed']); self.assertEqual(x['growth_bytes'],6); self.assertEqual(len(x['apk_sha256']),64)
    def test_threshold_inclusive(self): self.assertTrue(self.check(6/m.MIB)['passed'])
    def test_missing_ambiguous_or_wrong_asset(self):
        for assets in [[], self.release['assets']*2,[{'name':'other.apk','size':1}]]:
            self.release['assets']=assets
            with self.assertRaises(ValueError): self.check()
    def test_unpublished_wrong_tag_or_invalid_size(self):
        for field,value in [('draft',True),('prerelease',True),('tag_name','v2')]:
            old=self.release[field]; self.release[field]=value
            with self.assertRaises(ValueError): self.check()
            self.release[field]=old
        self.release['assets'][0]['size']=True
        with self.assertRaises(ValueError): self.check()
    def test_invalid_budget(self):
        for x in [-1,float('nan'),float('inf')]:
            with self.assertRaises(ValueError): self.check(x)
    def test_malformed_baseline_object(self):
        for release in [[], None, {'assets': None}, {'assets': ['bad']}]:
            with self.assertRaises(ValueError):m.measure(self.apk,release,'v1.0.0+1',5)
    def test_empty_candidate(self):
        self.apk.write_bytes(b'')
        with self.assertRaises(ValueError): self.check()

if __name__ == '__main__': unittest.main()
