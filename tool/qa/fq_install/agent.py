import sys,time,json,re,xml.etree.ElementTree as E,shlex
from pathlib import Path
import device as d
import probe as p
AGENT = C = NAME = EXE = PIN = None
records = []
FILE=p.FILES+'/shared_prefs/FlutterSharedPreferences.xml'
def emit(phase,**facts):
 r={'phase':phase,'at':round(time.monotonic(),2),**facts};records.append(r);print(json.dumps(r),flush=True)
def require_headroom():
 available=space()
 if available<800_000_000:raise RuntimeError('Insufficient certification headroom; no download started')
 return available
def space():
 raw=d.adb('shell','df','-k','/data');return int(raw.splitlines()[-1].split()[3])*1024
def gates():
 root=E.fromstring(d.adb('shell','cat','/data/user/0/'+d.PKG+'/shared_prefs/FlutterSharedPreferences.xml'))
 out=[]
 for n in root:
  key=n.get('name','')
  if 'oc.agentPhoneGate.' not in key:continue
  try:
   value=json.loads(n.text or '{}');v=value.get(AGENT)
   if isinstance(v,dict):
    f=json.loads(v.get('fingerprint','[]'))
    if len(f)>3:out.append({'profile':key.split('oc.agentPhoneGate.',1)[1],'matches':f[:4]==[AGENT,PIN,C['sha256'],'0.9.2'],'architecture':v.get('architecture')})
  except Exception:pass
 return out
BASE = None
# Only safe app-owned installation paths, public pin, and executable identity are projected.
def inventory(remove=False):
 if remove: p.require_idle_setup()
 script="""import os,json,pathlib,subprocess,shutil,signal,time
agent,exe,pin,sha,remove=PARAMS
base=pathlib.Path('/home/oc/.local/share/oc-agents')/agent
link=pathlib.Path('/home/oc/.local/bin')/exe
parent=base.parent
for ancestor in [pathlib.Path('/home/oc'),pathlib.Path('/home/oc/.local'),link.parent,pathlib.Path('/home/oc/.local/share'),parent]:
 if ancestor.is_symlink():raise RuntimeError('Symlinked ancestor; cleanup refused')
paths=[base,parent/('.lock-'+exe)]+list(link.parent.glob(exe+'.new.*'))
if any(item.is_symlink() for item in paths):raise RuntimeError('Symlinked cleanup target; refused')
pids=[]
for entry in pathlib.Path('/proc').iterdir():
 if not entry.name.isdigit():continue
 try:
  argv=(entry/'cmdline').read_bytes().split(b'\\0')
  if any(a.startswith(str(base).encode()+b'/payload/') or a.startswith(str(base).encode()+b'/'+pin.encode()+b'/payload/') or a==str(link).encode() for a in argv):
   pids.append(int(entry.name))
 except OSError:pass
size=int(subprocess.check_output(['du','-sk',str(base)]).split()[0])*1024 if base.exists() else 0
receipt=base/pin/'.oc-pin'
pinmatch=receipt.is_file() and not receipt.is_symlink() and receipt.read_text()==pin+'|x64|'+sha
linkmatch=link.is_symlink() and os.readlink(link)==str(base/pin/'launch')
if remove:
 if pids:raise RuntimeError('Target still has live processes; cleanup refused')
 if link.is_symlink():
  if not os.readlink(link).startswith(str(base)+'/'):raise RuntimeError('Unrelated link; cleanup refused')
 elif link.exists():raise RuntimeError('Unrelated executable; cleanup refused')
 # Validate every target before the first deletion.
 if any(item.is_symlink() for item in paths):raise RuntimeError('Symlinked tree; cleanup refused')
 if link.is_symlink():link.unlink()
 for item in paths:
  if item.is_dir():shutil.rmtree(item)
  elif item.exists():item.unlink()
print(json.dumps({'allocatedBytes':size,'pinMatches':pinmatch,'linkMatches':linkmatch,'targetPids':pids,'removed':remove,'leftovers':any(x.exists() or x.is_symlink() for x in paths+[link]),'staging':list(str(x.name) for x in base.glob('*.new')) if base.exists() else [],'lockPresent':(parent/('.lock-'+exe)).exists()}))
""".replace('PARAMS',repr([AGENT,EXE,PIN,C['sha256'],remove]))
 return json.loads(p.probe(script))
def close_sheet():
 ns=d.ui()
 if any('Check this phone' in d.text(n) for n in ns):return
 d.adb('shell','input','keyevent','4');time.sleep(.5)
def wait_terminal(max_seconds=150, ignore_job_id=None):
 start=time.monotonic();seen=[]
 while time.monotonic()-start<max_seconds:
  j=p.setup();c=j['components'].get('agent-'+AGENT,{})
  if j['jobId']==ignore_job_id:
   time.sleep(.25);continue
  if j['state'] in ['cancelled','interrupted','failed'] or (c.get('state') in ['done','skipped'] and j['state']=='done'):
   emit('native-terminal',state=j['state'],component=c);return j,c
  time.sleep(.4)
 raise RuntimeError('Target install exceeded bounded wait')
def run(agent_id, catalog, slow_download=False):
 global AGENT, C, NAME, EXE, PIN, BASE, records
 AGENT = agent_id
 C = catalog[AGENT]
 NAME, EXE, PIN = C['name'], C['executable'], C['version']
 BASE = '/home/oc/.local/share/oc-agents/' + AGENT
 records = []
 network_restore=None
 try:
  p.require_idle_setup()
  meta=d.adb('shell','dumpsys','package',d.PKG)
  if 'versionCode=2196 ' not in meta:
   p.require_idle_setup()
   emit('restore-normal-before-session', success=d.restore_normal_app())
  emit('space-before',availableBytes=space())
  if space()<800_000_000:raise RuntimeError('Insufficient certification headroom; no download started')
  active=p.setup()
  if active['state']=='running':raise RuntimeError('Another native setup is running; no mutation started')
  p.require_idle_setup()
  d.adb('shell','am','force-stop',d.PKG)
  d.launch_agents();close_sheet();emit('inventory-before',**inventory())
  # A pilot/pre-existing installation is cleared before the cancellation scenario.
  before=inventory()
  if before['leftovers']:
   emit('manual-preclean',**inventory(True))
   d.adb('shell','input','keyevent','4');d.launch_agents();time.sleep(1)
  emit('space-before-cancel',availableBytes=space())
  if slow_download:
   status=d.adb('emu','network','status')
   download=re.search(r'download speed:\s*([0-9.]+)\s*bits/s',status)
   upload=re.search(r'upload speed:\s*([0-9.]+)\s*bits/s',status)
   if not download or not upload or float(download[1])!=0 or float(upload[1])!=0:
    raise RuntimeError('Network rates are not the expected unlimited baseline; unchanged')
   network_restore='full'
   if 'OK' not in d.adb('emu','network','speed','1024'):
    raise RuntimeError('Network speed simulation failed')
   emit('network-download-limit',kilobitsPerSecond=1024,originalUploadBits=0,originalDownloadBits=0)
  previous_job=p.setup()['jobId']
  require_headroom()
  d.tap('Install '+NAME);d.tap('Install '+NAME);emit('install-dispatched')
  # Capture the actual Cancel action while preflight is running; cached bounds
  # allow a prompt tap for small downloads once byte progress becomes visible.
  cancelnodes=[n for n in d.ui() if d.text(n)=='Cancel setup']
  cancelnode=cancelnodes[-1] if cancelnodes else None
  didcancel=False
  start=time.monotonic()
  while time.monotonic()-start<150:
   j=p.setup();c=j['components'].get('agent-'+AGENT,{})
   if j['jobId']==previous_job:
    time.sleep(.08);continue
   done,total=c.get('done') or 0,c.get('total') or 0
   if c.get('state')=='running' and done>0 and done<(total or C['downloadBytes']):
    emit('mid-download',component=c,expectedDownloadBytes=C['downloadBytes'],partial=0<done<(total or C['downloadBytes']))
    if cancelnode is not None:d.tap_node(cancelnode)
    else:d.tap('Cancel setup')
    didcancel=True;emit('cancel-tapped');break
   if c.get('state') in ['done','failed']:break
   time.sleep(.08)
  if didcancel:
   j,c=wait_terminal(40);emit('cancel-result',cancelled=j['state']=='cancelled',**inventory());d.shot(AGENT+'-cancelled')
  else:
   emit('cancel-window-missed');j,c=wait_terminal(ignore_job_id=previous_job)
   # Still record the real first installation; retry follows scoped cleanup.
   close_sheet();emit('manual-reset-after-missed-window',**inventory(True));d.adb('shell','input','keyevent','4');d.launch_agents()
  if network_restore:
   if 'OK' not in d.adb('emu','network','speed',network_restore):
    raise RuntimeError('Network speed restoration failed')
   network_restore=None
   emit('network-restored-before-retry',unlimited=True)
  close_sheet()
  # Interrupted setup may remain a sheet; enter the target's setup frame again.
  ns=d.ui(); labels=[d.text(n) for n in ns]
  if 'Install '+NAME not in labels:
   d.adb('shell','input','keyevent','4');d.launch_agents()
  require_headroom()
  d.tap('Install '+NAME)
  ns=d.ui()
  if any(d.text(n)=='Install '+NAME for n in ns):d.tap('Install '+NAME,ns)
  emit('retry-dispatched',availableBytes=space());j,c=wait_terminal(ignore_job_id=j['jobId'])
  emit('retry-result',state=j['state'],component=c)
  if j['state']!='done':raise RuntimeError('Retry did not complete; no install certification')
  time.sleep(10)
  # First-ever installs can enter an automatic phone check after native done.
  # Drain that owned UI work before restarting for the explicit public check.
  idle_deadline=time.monotonic()+180
  while True:
   try:
    p.require_idle_setup();break
   except RuntimeError:
    if time.monotonic()>=idle_deadline:raise
    time.sleep(.5)
  d.adb('shell','am','force-stop',d.PKG)
  d.launch_agents()
  # Request a fresh public result even if a matching old gate survived removal.
  d.tap('Check this phone');fresh_check=False
  for check_poll in range(75):
   vals=d.texts()
   if any(NAME+' is ready on this phone' in v for v in vals):
    fresh_check=True;break
   if any(NAME in v and 'not ready' in v for v in vals):break
   time.sleep(.25)
  # Drain all later-agent checks before cleanup and releasing the device lock.
  for drain in range(70):
   vals=d.texts()
   if not any('Checking ' in text for text in vals):break
   time.sleep(.25)
  else:raise RuntimeError('Later phone check exceeded bounded wait')
  emit('fresh-phone-check-ui',passed=fresh_check,texts=vals)
  # Auto phone check clears and rewrites the target gate. Wait for its
  # bounded result and only inspect auth; never start a sign-in terminal.
  for i in range(100):
   proof=gates()
   if any(v['matches'] for v in proof):break
   time.sleep(.5)
  emit('phone-check',gates=proof)
  vals=d.texts();emit('account-free-app-state',texts=vals);d.shot(AGENT+'-after-install-check')
  inv=inventory();emit('installed-inventory',**inv)
  version_script="import subprocess,re,json;v=subprocess.run(["+repr('/home/oc/.local/bin/'+EXE)+",'--version'],capture_output=True,timeout=30);print(json.dumps({'exitCode':v.returncode,'versionMatches': bool(re.search(rb'(?<![0-9.])"+re.escape(PIN)+"(?![0-9.])',v.stdout))}))"
  emit('version-probe',**json.loads(p.probe(version_script,agent_user=True)))
  profile=next((v['profile'] for v in proof if v['matches']),None)
  if profile:
   auth_script='import subprocess,json; r=subprocess.run(["/bin/sh","-c",'+repr(C['authScript'])+'],capture_output=True,timeout=15);j=json.loads(r.stdout);print(json.dumps({k:v for k,v in j.items() if k in ["state","error"]}))'
   emit('auth-probe',**json.loads(p.probe(auth_script,agent_user=True,profile=profile)))
  launch_source=Path(__file__).with_name('launch.py').read_text()
  launch_script='import sys;sys.argv='+repr(['fqinstall_launch',AGENT,'--timeout','15'])+'\n'+launch_source
  launch_result=json.loads(p.probe(launch_script,agent_user=True));rawdir=launch_result.pop('rawDirectory',None)
  emit('account-free-cli-launch',**launch_result)
  if rawdir:
   p.probe('import shutil;shutil.rmtree('+repr(rawdir)+',ignore_errors=True)',agent_user=True)
  emit('cleanup-before',availableBytes=space(),**inventory())
  emit('manual-cleanup',**inventory(True));emit('cleanup-after',availableBytes=space(),**inventory())
  close_sheet();d.adb('shell','input','keyevent','4');d.launch_agents();d.shot(AGENT+'-after-cleanup');emit('cleanup-ui',texts=d.texts())
 except Exception as e:
  emit('error',code=type(e).__name__);raise
 finally:
  if network_restore:
   if 'OK' not in d.adb('emu','network','speed',network_restore):raise RuntimeError('Network restoration failed')
   emit('network-restored-after-error',unlimited=True)
  try:
   # Never reinstall/restart while setup is active or unknown.
   final_meta=d.adb('shell','dumpsys','package',d.PKG)
   if 'versionCode=2196 ' not in final_meta:
    p.require_idle_setup()
    emit('restore-normal-after-session', success=d.restore_normal_app())
   emit('normal-app-final',version2196='versionCode=2196 ' in d.adb('shell','dumpsys','package',d.PKG))
  finally:
   # Partial failures remain reviewable even if a safe restore was blocked.
   (d.OUT/(AGENT+'-device.json')).write_text(json.dumps(records,indent=2)+'\n')

if __name__ == '__main__':
 raise SystemExit('Use run.py; this helper has no standalone device entry point')
