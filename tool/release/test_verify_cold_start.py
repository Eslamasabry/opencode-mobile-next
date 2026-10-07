import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile
spec=importlib.util.spec_from_file_location('verify_cold',Path(__file__).with_name('verify_cold_start.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class VerifyColdStartTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.apk=Path(self.tmp.name)/'fixture.apk';
        with zipfile.ZipFile(self.apk,'w') as z:z.writestr('classes.dex',b'fixture')
        self.report={'schema':1,'apk_sha256':hashlib.sha256(self.apk.read_bytes()).hexdigest(),'apk_payload_sha256':m.payload_digest(self.apk),'serial':'emulator-5554','metric':'android_activity_total_time_ms','samples_ms':[100,200,300],'median_ms':200}
    def tearDown(self):self.tmp.cleanup()
    def test_valid(self):self.assertEqual(m.verify(self.apk,self.report),200)
    def test_stale_apk(self):
        
        with zipfile.ZipFile(self.apk,'w') as z:z.writestr('classes.dex',b'different')
        with self.assertRaises(ValueError):m.verify(self.apk,self.report)
    def test_different_test_signing_metadata_preserves_payload(self):
        with zipfile.ZipFile(self.apk,'a') as z:z.writestr('META-INF/CERT.RSA',b'different permitted signer')
        self.assertEqual(m.verify(self.apk,self.report),200)
    def test_service_metadata_is_executable_payload(self):
        with zipfile.ZipFile(self.apk,'a') as z:z.writestr('META-INF/services/entry',b'changed')
        with self.assertRaises(ValueError):m.verify(self.apk,self.report)
    def test_median_recomputed_not_trusted(self):
        self.report.update(samples_ms=[4000,5000,6000],median_ms=200)
        with self.assertRaises(ValueError):m.verify(self.apk,self.report)
    def test_device_and_sample_validation(self):
        for key,value in [('serial','another-device'),('samples_ms',[100]),('samples_ms',[True,100,200]),('metric','other')]:
            old=self.report[key];self.report[key]=value
            with self.assertRaises(ValueError):m.verify(self.apk,self.report)
            self.report[key]=old
if __name__=='__main__':unittest.main()
