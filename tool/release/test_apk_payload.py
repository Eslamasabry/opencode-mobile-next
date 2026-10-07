from pathlib import Path
import tempfile
import unittest
import zipfile
from apk_payload import payload_digest

class ApkPayloadTest(unittest.TestCase):
    def test_entry_order_and_zip_compression_do_not_change_executable_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            a=Path(directory)/'a.apk';b=Path(directory)/'b.apk'
            with zipfile.ZipFile(a,'w',zipfile.ZIP_STORED) as z:
                z.writestr('classes.dex',b'code');z.writestr('AndroidManifest.xml',b'manifest')
            with zipfile.ZipFile(b,'w',zipfile.ZIP_DEFLATED) as z:
                z.writestr('AndroidManifest.xml',b'manifest');z.writestr('classes.dex',b'code')
            self.assertEqual(payload_digest(a),payload_digest(b))
    def test_duplicate_entry_is_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            apk=Path(directory)/'a.apk'
            with zipfile.ZipFile(apk,'w') as z:
                z.writestr('classes.dex',b'code')
                with self.assertWarns(UserWarning):z.writestr('classes.dex',b'different')
            with self.assertRaises(ValueError):payload_digest(apk)
if __name__=='__main__':unittest.main()
