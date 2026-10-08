"""Run only a Claude status probe; caller must hold the shared emulator lock.
No APK replacement, direct account editing, logout, or raw process output.
The existing status CLI may perform its normal refresh; native lock checks remain active.
"""
import subprocess,json,re,shlex,time,argparse
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--build-dir',required=True)
parser.add_argument('--profile-id',required=True)
parser.add_argument('--mode',choices=['real','fixture'],default='real')
args=parser.parse_args()
assert re.fullmatch(r'[A-Za-z0-9_-]{1,80}',args.profile_id)
build=Path(args.build_dir)
s=['adb','-s','emulator-5554'];pkg='io.github.eslamasabry.opencode_mobile';base='/data/user/0/'+pkg+'/cache/ba-private-runtime-diagnostic'
def adb(*args):
 p=subprocess.run(s+list(args),capture_output=True,timeout=25)
 if p.returncode:
  print(json.dumps({'diagnostic_exit':p.returncode}))
  return p.stdout
 return p.stdout
try:
 package=adb('shell','dumpsys','package',pkg)
 assert re.search(rb'versionCode=2197 ',package), 'Normal app must be 2197'
 adb('shell','am','start','-n',pkg+'/.MainActivity')
 time.sleep(2)
 uid=adb('shell','su','0','stat','-c','%u','/data/user/0/'+pkg+'/files').decode().strip();assert uid.isdigit()
 adb('shell','su','0','mkdir','-p',base)
 adb('shell','su','0','chown',uid+':'+uid,base)
 adb('shell','su','0','chmod','755',base)
 for name in ['classes.dex','probe.txt','context_runner']:
  adb('push',str(build/name),'/data/local/tmp/ba-probe-'+name)
  adb('shell','su','0','mv','/data/local/tmp/ba-probe-'+name,base+'/'+name)
  adb('shell','su','0','chown',uid+':'+uid,base+'/'+name)
 adb('shell','su','0','chmod','755',base+'/context_runner')
 adb('shell','su','0','chmod','444',base+'/classes.dex')
 adb('shell','su','0','restorecon','-RF',base)
 apk=adb('shell','cmd','package','path',pkg).decode().strip().removeprefix('package:')
 native=apk.removesuffix('/base.apk')+'/lib/x86_64'
 pid=adb('shell','pidof',pkg).decode().strip();assert pid.isdigit()
 context=adb('shell','su','0','cat','/proc/'+pid+'/attr/current').decode().strip().strip('\x00');assert re.fullmatch(r'[A-Za-z0-9_:,]+',context)
 command='CLASSPATH='+base+'/classes.dex '+base+'/context_runner '+uid+' '+context+' /system/bin/app_process /system/bin io.github.eslamasabry.opencode_mobile.ProbeRunnerKt '+args.profile_id+' '+base+'/probe.txt '+native+' '+apk+' '+args.mode
 output=adb('shell','su','0','/system/bin/sh','-c',shlex.quote(command))
 allowed=re.findall(rb'(?m)^PROBE_(STATE|ERROR|DIAGNOSTIC|EXCEPTION|ROOTPROC|SELFTASK|CHILDREN|SUBREAPER)=([A-Za-z]+)\r?$',output)
 print(json.dumps({'fixed_projection':{k.decode():v.decode() for k,v in allowed if k != b'DIAGNOSTIC'},'stages':[v.decode() for k,v in allowed if k == b'DIAGNOSTIC']}))
finally:
 adb('shell','su','0','rm','-rf',base)
 package=adb('shell','dumpsys','package',pkg)
 print(json.dumps({'normal_app_version_2197':bool(re.search(rb'versionCode=2197 ',package)),
                   'diagnostic_cache_removed':subprocess.run(s+['shell','su','0','test','-e',base],capture_output=True,timeout=25).returncode==1}))
