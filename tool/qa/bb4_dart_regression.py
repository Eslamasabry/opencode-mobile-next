"""Focused removed-fix controls. Restore exact original bytes even on failure."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parents[2]; OUT=ROOT/'docs/qa/BB4-2026-10-07'
F=str(Path.home()/'.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter')
def run(label, files, name=None):
 cmd=[str(ROOT/'tool/qa/machine_lock.sh'),'test','--',F,'test','--no-pub','--concurrency=1','--reporter','expanded',*files]
 if name: cmd+=['--plain-name',name]
 with (OUT/(label+'.txt')).open('w') as output: code=subprocess.run(cmd,cwd=ROOT,stdout=output,stderr=subprocess.STDOUT).returncode
 return code,(OUT/(label+'.txt')).read_text()
watch='lib/builtin/reply_watch.dart'
controls=[
 ('obsolete-on',watch,'      if (!_currentLeaseIntent(generation)) return;','', 'idle before queued acquire skips obsolete on'),
 ('required-off',watch,'      if (!on) {','      if (!on) {\n        if (!_currentLeaseIntent(generation)) return;', 'rapid idle and busy preserves the required off boundary'),
 ('late-acquire',watch,'      if (!_currentLeaseIntent(generation)) {\n        // Late acquisition after idle/disposal must never become visible or\n        // survive just because the next queued off has not run yet.\n        await _releaseLease();\n        return;\n      }','', 'late acquire after idle drains without reporting held'),
 ('native-cap',watch,'      if (status.capped) {','      if (false) {','native cap stops renewals without reopening a busy lease'),
 ('refused-renewal',watch,'      } else if (!status.held) {\n        await _releaseLease();','      } else if (!status.held) {','rejected renewal closes only its lease and stops renewing'),
 ('failed-handoff',watch,'        await _releaseLease();\n        if (_currentLeaseIntent(generation)) _stopRenewing();\n        return;','        if (_currentLeaseIntent(generation)) _stopRenewing();\n        return;', 'acquire exception drains partial lease and leaves chain usable'),
 ('independent-id',watch,'_leaseId = leaseId ?? _newLeaseId(),',"_leaseId = leaseId ?? 'chat.shared',",'two watches use distinct IDs and cleanup preserves other work'),
 ('channel-result','lib/builtin/builtin_linux.dart','    return raw is Map<Object?, Object?>\n        ? BuiltinWorkLeaseStatus.fromMap(raw)\n        : const BuiltinWorkLeaseStatus();','    return const BuiltinWorkLeaseStatus();','named chat acquisition uses the frozen channel contract'),
]
for label,file,before,after,name in controls:
 if len(sys.argv)>1 and [x[0] for x in controls].index(label)<[x[0] for x in controls].index(sys.argv[1]): continue
 p=ROOT/file; original=p.read_bytes();text=original.decode(); assert text.count(before)==1,(label,text.count(before))
 try:
  p.write_text(text.replace(before,after,1))
  tests=['test/builtin_work_leases_test.dart' if label=='channel-result' else 'test/reply_watch_test.dart']
  code,log=run('dart-red-'+label,tests,name)
  assert code==1 and '[E]' in log and 'Expected:' in log and 'Some tests failed.' in log,label
  print(label+': expected behavior assertion failure',flush=True)
 finally:p.write_bytes(original)
code,_=run('dart-final-restored',['test/reply_watch_test.dart','test/builtin_work_leases_test.dart','test/this_phone_speed_test.dart'])
assert code==0
print('Restored affected Dart lease and timing checks PASS',flush=True)
