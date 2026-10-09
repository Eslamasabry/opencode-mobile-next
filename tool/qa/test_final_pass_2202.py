"""Regression checks for the current final-pass batch and opt-in slow rows."""
import unittest
from pathlib import Path
from unittest.mock import patch
from tool.qa import final_pass as batch
from tool.qa.fq9 import common
from tool.qa import bd7_device_saved_report as bd7

class CurrentBatchTests(unittest.TestCase):
    def test_normal_is_2202(self):
        args = batch.parser().parse_args([])
        self.assertEqual(args.normal_apk.name, 'oc-2202.apk')
        self.assertEqual(batch.plan(args)['normalBuild'], 2202)

    def test_fast_selection_excludes_long_background_and_recording(self):
        args = batch.parser().parse_args(['--fast'])
        plan = batch.plan(args)
        self.assertEqual(plan['rows'], [row for row in batch.ROWS
                                      if row not in ('fq9-background', 'demo')])
        self.assertEqual(plan['deferredRows'], ['fq9-background', 'demo'])
        self.assertEqual(plan['backgroundCheckpointsSeconds'], [])
        self.assertFalse(plan['deviceTouched'])

    def test_current_protocol_driver_targets_2202(self):
        self.assertEqual(common.CANDIDATE_BUILD, 2202)

    def test_saved_report_accepts_verified_current_artifact(self):
        import tempfile
        with tempfile.TemporaryDirectory() as folder:
            apk = Path(folder)/'candidate.apk'
            apk.write_bytes(b'fixture')
            Path(str(apk)+'.sha256').write_text('a'*64)
            identity = dict(build=2202, signer=common.LOCAL_SIGNER, version='1.2.0')
            with patch.object(bd7, 'apk_identity', return_value=identity), \
                 patch.object(bd7, 'verify_artifact'):
                self.assertEqual(bd7.load_artifact(apk).build, 2202)

if __name__ == '__main__':
    unittest.main()
