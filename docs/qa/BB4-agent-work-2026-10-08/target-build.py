import os,subprocess,time,threading,json,uuid,hashlib,shutil,signal
from pathlib import Path
ROOT=Path('/home/eslam/Storage/Code/oc_app-sol-bb');OUT=ROOT/'docs/qa/BB4-agent-work-2026-10-08'; marker='bb4-target-'+uuid.uuid4().hex
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
import zipfile
with zipfile.ZipFile(ROOT/'build/app/outputs/flutter-apk/app-release.apk') as prior:
 releaseEngineSha256=hashlib.sha256(prior.read('lib/x86_64/libflutter.so')).hexdigest()
env=os.environ.copy();env.update(JAVA_HOME='/home/eslam/Storage/tmp/codex-audit3-temurin17/jdk-17.0.20.1+1',GRADLE_OPTS='-Dorg.gradle.jvmargs="-Xmx1g -XX:MaxMetaspaceSize=512m" -Dorg.gradle.workers.max=1',OC_BB3_BUILD_OWNER=marker)
flutter='/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter'
cmd=[str(ROOT/'tool/qa/machine_lock.sh'),'build','--',flutter,'build','apk','--release','--no-pub','--no-android-gradle-daemon','--build-number=2214','--target-platform=android-x64','--android-project-arg=ocStableEngineQa=true','--android-project-arg=ocBuiltinRuntimeQa=true','--android-project-arg=kotlin.compiler.execution.strategy=in-process']
watch=threading.Thread(target=discover,daemon=True);watch.start();before=time.monotonic()
try:
 with (OUT/'target-work-build.txt').open('w') as f:
  f.write('BB4 independent work lease target; pinned Flutter release, build lock, JDK17, one Gradle worker/1GB.\n');f.flush();p=subprocess.Popen(cmd,cwd=ROOT,env=env,stdout=f,stderr=subprocess.STDOUT);code=p.wait();f.write('\nPROCESS_EXIT='+str(code)+'\n');f.flush()
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
 target=ROOT/'build/app/outputs/flutter-apk/app-release.apk';duplicate=ROOT/'build/app/outputs/apk/release/app-release.apk'
 if code==0:
  digest=hashlib.sha256(target.read_bytes()).hexdigest()
  if duplicate.is_file() and hashlib.sha256(duplicate.read_bytes()).hexdigest()==digest:duplicate.unlink()
  signer=subprocess.run(['/home/eslam/Android/Sdk/build-tools/36.0.0/apksigner','verify','--print-certs',str(target)],capture_output=True,text=True,check=True)
  import re
  certs=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)',signer.stdout);assert certs==['1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c']
  aapt=subprocess.run(['/home/eslam/Android/Sdk/build-tools/36.0.0/aapt','dump','badging',str(target)],capture_output=True,text=True,check=True);assert "versionCode='2214'" in aapt.stdout
  assert "application-debuggable" not in aapt.stdout
  manifest=subprocess.run(['/home/eslam/Android/Sdk/build-tools/36.0.0/aapt','dump','xmltree',str(target),'AndroidManifest.xml'],capture_output=True,text=True,check=True)
  assert '.BuiltinInstallerProducerQaService' not in manifest.stdout and ':bb9producer' not in manifest.stdout
  with zipfile.ZipFile(target) as built:
   assert hashlib.sha256(built.read("lib/x86_64/libflutter.so")).hexdigest()==releaseEngineSha256
   assert "lib/x86_64/libapp.so" in built.namelist()
   assert "assets/flutter_assets/kernel_blob.bin" not in built.namelist()
  artifact={'sha256':digest,'versionCode':2214,'signer':certs[0],'elapsedSeconds':round(time.monotonic()-before,1),'bytes':target.stat().st_size,'privateQaDebuggable':False,'ordinaryAppProducerComponent':False,'unchangedReleaseEngineSha256':releaseEngineSha256};(OUT/'target-work-artifact.json').write_text(json.dumps(artifact,indent=2)+'\n');print(json.dumps(artifact),flush=True)
 with guard:values=list(owned.values())
 (OUT/'target-work-owned-processes.json').write_text(json.dumps({'owned':values,'remaining':[v for v in values if v['pid']!=os.getpid() and ticks(v['pid'])==v['startTicks']],'intermediatesAbsent':not (ROOT/'build/app/intermediates').exists()},indent=2)+'\n')
 raise SystemExit(code)
finally:
 done.set()
