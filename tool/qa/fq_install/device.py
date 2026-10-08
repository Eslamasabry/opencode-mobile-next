import subprocess,time,xml.etree.ElementTree as E,re,json,sys
from pathlib import Path
from io import BytesIO
from PIL import Image
PKG='io.github.eslamasabry.opencode_mobile'
OUT = None
_SESSION = False

def configure(output):
 global OUT, _SESSION
 OUT = Path(output)
 OUT.mkdir(parents=True, exist_ok=True)
 _SESSION = True

def end_session():
 global _SESSION
 _SESSION = False
def adb(*args,raw=False,timeout=50):
 if not _SESSION: raise RuntimeError('Use run.py; device helpers require the shared lock')
 r=subprocess.run(['adb','-s','emulator-5554',*args],capture_output=True,timeout=timeout)
 if r.returncode:raise RuntimeError('adb '+args[0]+' failed: '+str(r.returncode))
 return r.stdout if raw else r.stdout.decode(errors='replace')
def ui():
 for attempt in range(3):
  try:
   adb('shell','uiautomator','dump','/data/local/tmp/fq-install.xml');break
  except RuntimeError:
   if attempt==2:raise
   time.sleep(1)
 return E.fromstring(adb('shell','cat','/data/local/tmp/fq-install.xml')).findall('.//node')
def text(n):return (n.get('text') or n.get('content-desc') or '').replace('\u2068','').replace('\u2069','')
def tap_node(n):
 a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')))
 adb('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(.4)
def tap(label,nodes=None,contains=False):
 nodes=nodes or ui();found=[n for n in nodes if (label in text(n) if contains else label==text(n))]
 if not found and label=='Check this phone':
  merged=[n for n in nodes if 'Check this phone' in text(n)]
  if merged:
   a,b,c,e=map(int,re.findall(r'\d+',merged[-1].get('bounds')))
   # APK2196 merges this final KitRow's semantics into its group's label.
   # The observed screenshot places it at the end of the group.
   adb('shell','input','tap',str((a+c)//2),str(e-84));time.sleep(.4);return
 if not found:
  shot('navigation-target-missing')
  raise RuntimeError('Target unavailable: '+label)
 tap_node(found[-1])
def texts(nodes=None):
 # Closed app-authored status projection; never persist arbitrary screen copy.
 names = ['Claude Code', 'Codex', 'Gemini CLI', 'Qwen Code', 'Goose', 'Oh My Pi', 'fx']
 safe = {'Agents', 'Check this phone', 'Ready', 'Sign in needed', 'Phone check needed',
         "Can't reopen old conversations", 'Not installed', 'Checking sign-in…',
         'Install', 'Cancel setup', 'Resume', 'Sign in', 'Setup stopped before it finished. Install again to continue.',
         "Setup didn't finish. Install again to try once more."}
 out = []
 for node in nodes or ui():
  for line in text(node).splitlines():
   if line in safe or line in names or any(line in [name+' is ready on this phone.', name+" didn't pass the check.", 'Checking '+name+'…', 'Check '+name, 'Install '+name, 'Sign in to '+name, 'Sign in with '+name] for name in names):
    if line not in out: out.append(line)
 return out
def shot(name):
 im=Image.open(BytesIO(adb('exec-out','screencap','-p',raw=True))).convert('RGB');im.thumbnail((540,1200));im.save(OUT/(name+'.jpg'),quality=73)
def launch_agents():
 import probe
 probe.require_idle_setup()
 adb('shell','input','keyevent','224');adb('shell','input','keyevent','82');adb('shell','am','start','-n',PKG+'/.MainActivity');time.sleep(2)
 for i in range(20):
  ns=ui();ts=[text(n) for n in ns]
  print(json.dumps({'navigation':i,'settings': 'Settings' in ts,'agents': any('Check this phone' in t for t in ts),'serverList':any('In-app Ubuntu' in t for t in ts),'scrim':'Scrim' in ts}),flush=True)
  if any('Check this phone' in t for t in ts):return
  if 'Settings' in ts:
   tap('Settings',ns);time.sleep(.5);tap('Agents');time.sleep(2);continue
  if i>=2 and 'Scrim' in ts and 'Cancel setup' not in ts:
   adb('shell','input','keyevent','4');time.sleep(.5);continue
  builtins=[n for n in ns if 'In-app Ubuntu' in text(n) and 'Connected' in text(n) and n.get('clickable')=='true']
  if builtins and 'OpenCode' in ts and 'Add server' in ts:
   tap_node(builtins[0]);time.sleep(2);continue
  if 'OpenCode Mobile' in ts:
   adb('shell','am','start','-n',PKG+'/.MainActivity');time.sleep(2)
  time.sleep(1)
 im=Image.open(BytesIO(adb('exec-out','screencap','-p',raw=True))).convert('RGB');im.thumbnail((540,1200));im.save(OUT/'navigation-not-ready.jpg',quality=73)
 raise RuntimeError('Main app not ready')
if __name__ == '__main__':
 raise SystemExit('Use run.py; this helper has no standalone device entry point')


def restore_normal_app():
 """Restore the approved signer with install -r -d, only while setup is idle."""
 import os
 import shutil
 import probe
 probe.require_idle_setup()
 apk = Path('/home/eslam/Storage/tmp/oc-apk-share/oc-2196.apk')
 if not apk.is_file():
  raise RuntimeError('Approved normal APK is unavailable')
 sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or str(Path.home()/'Android/Sdk'))
 candidates = sorted((sdk/'build-tools').glob('*/apksigner'))
 signer = shutil.which('apksigner') or (str(candidates[-1]) if candidates else None)
 if signer is None:
  raise RuntimeError('APK signer verification tool unavailable')
 checked = subprocess.run([signer,'verify','--print-certs',str(apk)],capture_output=True,timeout=30)
 certificate = '1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c'
 if checked.returncode or certificate not in checked.stdout.decode(errors='replace').lower():
  raise RuntimeError('Approved normal APK signer did not match')
 probe.require_idle_setup()
 if 'Success' not in adb('install','-r','-d',str(apk),timeout=90):
  raise RuntimeError('Normal APK restore failed')
 if 'versionCode=2196 ' not in adb('shell','dumpsys','package',PKG):
  raise RuntimeError('Normal app version did not match after restore')
 return True
