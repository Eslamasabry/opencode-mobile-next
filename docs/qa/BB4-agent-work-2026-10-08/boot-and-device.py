import subprocess,time,os,json
from pathlib import Path
log=Path('/home/eslam/Storage/Code/oc_app-sol-bb/docs/qa/BB4-agent-work-2026-10-08/emulator-readiness.json')
def adb(*args):return subprocess.run(['adb','-s','emulator-5554',*args],capture_output=True,text=True,timeout=5)
booted=False
if adb('get-state').returncode!=0:
 with Path('/tmp/oc-bb4-emulator-boot.log').open('w') as f:
  child=subprocess.Popen(['/home/eslam/Android/Sdk/emulator/emulator','-avd','OC_API35','-port','5554','-no-window','-no-audio','-no-snapshot','-gpu','swiftshader_indirect'],stdout=f,stderr=subprocess.STDOUT,start_new_session=True,close_fds=True)
 booted=True
 deadline=time.monotonic()+120
 while time.monotonic()<deadline:
  if child.poll() is not None:raise RuntimeError('known_dev_emulator_start_failed')
  result=adb('shell','getprop','sys.boot_completed')
  if result.returncode==0 and result.stdout.strip()=='1':break
  time.sleep(1)
 else:raise RuntimeError('known_dev_emulator_readiness_timeout')
 if 'uid=0(' not in adb('shell','id').stdout:
  if adb('root').returncode!=0:raise RuntimeError('dev_private_access_unavailable')
  deadline=time.monotonic()+20
  while time.monotonic()<deadline:
   if 'uid=0(' in adb('shell','id').stdout:break
   time.sleep(.5)
  else:raise RuntimeError('dev_private_access_unavailable')
log.write_text(json.dumps({'serial':'emulator-5554','avd':'OC_API35','existingDataPreserved':True,'snapshotLoad':False,'startedExistingAvd':booted,'privateAccess':True},indent=2)+'\n')
print('PASS existing_OC_API35_ready_same_data_no_snapshot_or_wipe',flush=True)
import sys
os.execv(sys.executable,[sys.executable,'/tmp/oc-bb4-agent-normal-restore-session.py',*sys.argv[1:]])
