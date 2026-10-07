from pathlib import Path
import os
import subprocess
import tempfile
import unittest
SCRIPT=Path(__file__).with_name('machine_lock.sh').resolve()
class MachineLockTest(unittest.TestCase):
    def test_ci_override_holds_requested_build_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            lock=Path(directory)/'locks/build.lock'
            # Child proves the exact override is already exclusively held.
            child='import subprocess,sys; r=subprocess.run(["flock","-n",sys.argv[1],"true"]);sys.exit(0 if r.returncode==1 else 1)'
            result=subprocess.run([str(SCRIPT),'build','--','python3','-c',child,str(lock)],env={**os.environ,'OC_BUILD_LOCK_FILE':str(lock),'OC_LOCK_DIR':directory},capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertTrue(lock.exists())
if __name__=='__main__':unittest.main()
