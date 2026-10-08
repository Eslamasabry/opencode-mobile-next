"""Run exact BB9 removed-integration regressions, restoring every source in finally."""
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'docs/qa/BB9-2026-10-07'
F=str(Path.home()/'.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter')
def run(label,tests,name=None):
 cmd=[str(ROOT/'tool/qa/machine_lock.sh'),'test','--',F,'test','--no-pub','--concurrency=1','--reporter','expanded',*tests]
 if name:cmd+=['--plain-name',name]
 with (OUT/(label+'.txt')).open('w') as f:r=subprocess.run(cmd,cwd=ROOT,stdout=f,stderr=subprocess.STDOUT)
 return r.returncode,(OUT/(label+'.txt')).read_text()
p=ROOT/'lib/builtin/agents/agent_scripts.dart'; original=p.read_bytes();old=subprocess.check_output(['git','show','2fe344b57:lib/builtin/agents/agent_scripts.dart'],cwd=ROOT)
try:
 p.write_bytes(old)
 for label,name in [('catalog-cold','Claude check restores interrupted code before probing its link'),('catalog-lock','Claude check refuses another installer lock without changing code'),('catalog-activation','failed Claude active probe restores the prior pinned executable')]:
  code,log=run('dart-red-'+label,['test/phone_agent_scripts_test.dart'],name)
  assert code==1 and '[E]' in log and 'Some tests failed.' in log,label
  print(label+': expected assertion failure',flush=True)
finally:p.write_bytes(original)
p=ROOT/'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupRunner.kt';original=p.read_text()
before='val started = linux.startInstaller(script, NativeInstallerOwnership.targetsForComponent(spec.id),\n            InstallerOperation.INSTALL, agentUser = spec.agentUser)'
assert original.count(before)==1
try:
 p.write_text(original.replace(before,'val started = linux.start(script, null, agentUser = spec.agentUser)',1))
 code,log=run('dart-red-installer-routing',['test/setup_runner_native_test.dart'],'native setup persistence: installer-routed')
 assert code==1 and '[E]' in log and 'Some tests failed.' in log
 print('installer route: expected assertion failure',flush=True)
finally:p.write_text(original)
code,log=run('dart-final-restored',['test/phone_agent_scripts_test.dart','test/setup_runner_native_test.dart','test/setup_component_update_test.dart'])
assert code==0
print('All affected files restored green',flush=True)
