import sys,subprocess,shlex,json,re
import device as d
ROOT='/data/user/0/'+d.PKG+'/files/linux/ubuntu'
FILES='/data/user/0/'+d.PKG+'/files'
def probe(script,agent_user=False,profile=None,observation=False):
 if observation and profile is not None:raise ValueError('Observation cannot enter an account profile')
 if profile is not None and not re.fullmatch(r'[A-Za-z0-9_-]{1,80}',profile):raise RuntimeError('Invalid profile identity')
 apk=d.adb('shell','cmd','package','path',d.PKG).strip().removeprefix('package:')
 uid=d.adb('shell','stat','-c','%u',FILES).strip()
 if not uid.isdigit():raise RuntimeError('Invalid app UID')
 native=apk.removesuffix('/base.apk')+'/lib/x86_64'
 args=[native+'/libproot.so','--kill-on-exit','--link2symlink','-L','--sysvipc','--rootfs='+ROOT,'--bind=/dev','--bind=/proc','--bind=/sys','--bind='+FILES+'/projects:/root/projects','--cwd=/home/oc' if agent_user else '--cwd=/root']
 args+=['--change-id=1000:1000'] if agent_user else ['--root-id']
 home='/home/oc/.oc-profiles/'+profile if profile else '/home/oc' if agent_user else '/root'
 args+=['/usr/bin/env','-i','HOME='+home,'PATH=/home/oc/.local/bin:/home/oc/.local/node/bin:/usr/bin:/bin','LANG=C.UTF-8','CLAUDE_CONFIG_DIR='+home+'/claude','CODEX_HOME='+home+'/codex','/usr/bin/python3','-c',script]
 env={'PROOT_LOADER':native+'/libproot-loader.so','PROOT_TMP_DIR':ROOT+'/tmp','LD_LIBRARY_PATH':native}
 cmd=' '.join(k+'='+shlex.quote(v) for k,v in env.items())+' '+shlex.join(args)
 # Read-only QA inventory must not create an unregistered app-UID root
 # while native ownership checks run. This changes instrumentation only;
 # never use observation mode for auth or mutation/cleanup proof.
 execution_uid='0' if observation else uid
 return d.adb('shell','su '+execution_uid+' /system/bin/sh -c '+shlex.quote(cmd),timeout=80)
def setup():
 raw=d.adb('shell','cat',FILES+'/linux/setup.json')
 j=json.loads(raw)
 if not isinstance(j,dict) or not isinstance(j.get('components',{}),dict):raise RuntimeError('Invalid setup snapshot')
 return {'jobId':j.get('jobId'),'state':j.get('state'),'current':j.get('current'),'components': {k:{f:v.get(f) for f in ['state','stage','done','total','version']} for k,v in j.get('components',{}).items()}}
if __name__ == '__main__':
 raise SystemExit('Use run.py; this helper has no standalone device entry point')


def require_idle_setup():
 """Fail closed before changing APK/process lifetime or deleting payloads."""
 state = setup()['state']
 if state not in ('done', 'cancelled', 'interrupted', 'failed', 'idle'):
  raise RuntimeError('Native setup is active or unknown; no mutation allowed')
 # Native ownership is not persisted until preflight hands the job over.
 # Preserve another install/check already visible in the foreground as well.
 labels = [d.text(node) for node in d.ui()]
 if any('Cancel setup' in label or ('Checking ' in label and '…' in label) for label in labels):
  raise RuntimeError('App setup/check is active; no restart or cleanup allowed')
