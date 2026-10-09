import os,subprocess,time,threading,json,uuid,hashlib,shutil,signal
from pathlib import Path
ROOT=Path('/home/eslam/Storage/Code/oc_app-sol-bb');OUT=ROOT/'docs/qa/BB4-agent-work-2026-10-08'; marker='bb4-runner-'+uuid.uuid4().hex
owned={};guard=threading.Lock();done=threading.Event()
def ticks(pid):
 try:
  text=Path('/proc/'+str(pid)+'/stat').read_text();return int(text[text.rfind(')')+1:].split()[19])
 except (OSError,ValueError,IndexError):return None
def discover():
 while not done.is_set():
  for entry in Path('/proc').iterdir():
   if not entry.name.isdecimal():continue
   try:
    with (entry/'environ').open('rb') as f:env=f.read(1048576)
    if b'OC_BB3_BUILD_OWNER='+marker.encode()+b'\0' not in env:continue
    pid=int(entry.name);start=ticks(pid)
    if start is None:continue
    cmd=(entry/'cmdline').read_bytes();daemon=b'org.gradle.launcher.daemon.bootstrap.GradleDaemon' in cmd
    with guard:owned[pid]={'pid':pid,'startTicks':start,'gradleDaemon':daemon}
   except (OSError,ValueError):continue
  done.wait(.6)
env=os.environ.copy();env.update(JAVA_HOME='/home/eslam/Storage/tmp/codex-audit3-temurin17/jdk-17.0.20.1+1',GRADLE_OPTS='-Dorg.gradle.jvmargs="-Xmx1g -XX:MaxMetaspaceSize=512m" -Dorg.gradle.workers.max=1',OC_BB3_BUILD_OWNER=marker)
flutter='/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter'
cmd=[str(ROOT/'tool/qa/machine_lock.sh'),'build','--','/home/eslam/.gradle/wrapper/dists/gradle-9.5.0-all/aca6g93cdtcf0oapcfka748qh/gradle-9.5.0/bin/gradle','-p','android','--no-daemon','--max-workers=1','-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=512m','-Pkotlin.compiler.execution.strategy=in-process','-PocBuiltinRuntimeQa=true','-PocStableEngineQa=true','-Ptarget-platform=android-x64','-Pflutter.versionCode=2214',':app:assembleReleaseAndroidTest']
oldTarget=hashlib.sha256((ROOT/'build/app/outputs/flutter-apk/app-release.apk').read_bytes()).hexdigest()
watch=threading.Thread(target=discover,daemon=True);watch.start();before=time.monotonic()
try:
 with (OUT/'runner-work-build.txt').open('w') as f:
  f.write('BB9 live-peer and queue private regressions runner; pinned Flutter release, build lock, JDK17, one Gradle worker/1GB.\n');f.flush();p=subprocess.Popen(cmd,cwd=ROOT,env=env,stdout=f,stderr=subprocess.STDOUT);code=p.wait();f.write('\nPROCESS_EXIT='+str(code)+'\n');f.flush()
 print('target_build_exit='+str(code),flush=True)
 for _ in range(15):
  with guard:daemons=[v for v in owned.values() if v['gradleDaemon'] and ticks(v['pid'])==v['startTicks']]
  if not daemons:break
  time.sleep(1)
 for value in daemons:
  if ticks(value['pid'])==value['startTicks']:os.kill(value['pid'],signal.SIGTERM)
 time.sleep(1)
 for value in daemons:
  if ticks(value['pid'])==value['startTicks']:os.kill(value['pid'],signal.SIGKILL)
 done.set();watch.join(timeout=3)
 shutil.rmtree(ROOT/'build/app/intermediates',ignore_errors=True)
 target=ROOT/'build/app/outputs/apk/androidTest/release/app-release-androidTest.apk';duplicate=ROOT/'build/app/outputs/flutter-apk/app-release-androidTest.apk'
 if code==0:
  digest=hashlib.sha256(target.read_bytes()).hexdigest()
  if duplicate.is_file() and hashlib.sha256(duplicate.read_bytes()).hexdigest()==digest:duplicate.unlink()
  signer=subprocess.run(['/home/eslam/Android/Sdk/build-tools/36.0.0/apksigner','verify','--print-certs',str(target)],capture_output=True,text=True,check=True)
  import re
  certs=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)',signer.stdout);assert certs==['1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c']
  aapt=subprocess.run(['/home/eslam/Android/Sdk/build-tools/36.0.0/aapt','dump','badging',str(target)],capture_output=True,text=True,check=True);assert "package:" in aapt.stdout
  assert hashlib.sha256((ROOT/'build/app/outputs/flutter-apk/app-release.apk').read_bytes()).hexdigest()==oldTarget
  artifact={'sha256':digest,'versionCode':2214,'signer':certs[0],'elapsedSeconds':round(time.monotonic()-before,1),'bytes':target.stat().st_size};(OUT/'runner-work-artifact.json').write_text(json.dumps(artifact,indent=2)+'\n');print(json.dumps(artifact),flush=True)
 with guard:values=list(owned.values())
 (OUT/'runner-work-owned-processes.json').write_text(json.dumps({'owned':values,'remaining':[v for v in values if v['pid']!=os.getpid() and ticks(v['pid'])==v['startTicks']],'intermediatesAbsent':not (ROOT/'build/app/intermediates').exists()},indent=2)+'\n')
 raise SystemExit(code)
finally:done.set()
