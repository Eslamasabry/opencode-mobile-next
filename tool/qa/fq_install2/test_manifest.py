import json
from pathlib import Path
import tempfile
import unittest
from manifest import load, FLAG, FLOOR

class Tests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup); self.path=Path(self.temp.name)/'receipt.json'
        self.normal=dict(apk='/tmp/normal.apk',build=2197,sha256='a'*64,sourceRevision='b'*40,dartDefines={})
    def check(self,value,case='launch'):
        self.path.write_text(json.dumps(value)); return load(self.path,case)
    def test_normal_plan_is_offline(self): self.assertEqual(self.check({'normal':self.normal})['normal']['build'],2197)
    def test_low_storage_requires_separate_flagged_receipt(self):
        with self.assertRaises(ValueError): self.check({'normal':self.normal},'low-storage')
        guard={**self.normal,'dartDefines':{FLAG:FLOOR},'apk':'/tmp/guard.apk','sha256':'c'*64}
        self.assertIn('guard',self.check({'normal':self.normal,'guard':guard},'low-storage'))
    def test_same_binary_cannot_be_normal_and_flagged_guard(self):
        guard={**self.normal,'dartDefines':{FLAG:FLOOR},'apk':'/tmp/guard.apk'}
        with self.assertRaisesRegex(ValueError,'^artifact_guard_matches_normal$'):
            self.check({'normal':self.normal,'guard':guard},'low-storage')
    def test_normal_guard_cannot_stay_enabled(self):
        with self.assertRaises(ValueError): self.check({'normal':{**self.normal,'dartDefines':{FLAG:FLOOR}}})
    def test_unknown_defines_builds_or_hashes_fail_closed(self):
        for key,value in [('build',2196),('build',True),('sha256','unknown'),('apk','relative.apk'),('dartDefines',{'providerKey':'secret-test-marker'})]:
            with self.subTest(key=key,value=value):
                with self.assertRaises(ValueError): self.check({'normal':{**self.normal,key:value}})
    def test_duplicate_keys_rejected(self):
        self.path.write_text('{"normal":{},"normal":{}}')
        with self.assertRaises(ValueError): load(self.path,'launch')
if __name__=='__main__': unittest.main()
