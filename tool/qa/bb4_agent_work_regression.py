"""Removed-fix controls for helper chat CPU ownership; restores exact source bytes.

Run under the shared test lock. Do not run alongside a source-editing worker or
an APK compile. Only the affected Dart behavior tests run; no device/network.
"""
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/qa/BB4-agent-work-2026-10-08'
FLUTTER=Path.home()/'.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter'
WATCH=ROOT/'lib/builtin/phone_agent_work_watch.dart'
CONTROLS=(
 ('unknown-creation','    if (busy != true) return;','', 'unknown never creates a hold; known busy renews the same ID'),
 ('obsolete-on','      if (!_current(run)) return;','', 'idle before queued on skips obsolete acquire but still sends off'),
 ('late-disposed-owner','      if (!_retained(run)) {\n        await _off(run);\n        return;\n      }','      if (!_retained(run)) return;', 'late acquire after dispose releases only its own ID'),
 ('mandatory-old-off','    _chain = _chain.then((_) => _off(run));','    _chain = _chain.then((_) async { if (_retained(run)) await _off(run); });', 'rapid idle busy retains mandatory old off before new on'),
 ('native-cap','      if (status.capped) {\n        // Keep the exhausted logical name until actual idle/owner release.\n        _close(run);','      if (status.capped) {', 'native cap never reopens on true or unknown until confirmed idle'),
 ('busy-tombstone','      await _hold(run.profileId, run.id, true, Duration.zero);','      await _hold(run.profileId, run.id, false, _ttl);', 'combined busy survives CPU closure until every run is actually idle'),
 ('late-closed-owner','      if (!_retained(run)) {\n        await _off(run);\n        return;\n      }','      if (!_current(run)) {\n        await _off(run);\n        return;\n      }', 'late capped result after clock closure retains its busy key'),
 ('independent-names',"'agent.${List.generate(16, (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0')).join()}';", "'agent.shared';", 'independent watchers never release each other'),
)
def run(path,name=None,files=None):
 command=[str(FLUTTER),'test','--no-pub','--concurrency=1','--reporter','expanded',*(files or ['test/phone_agent_work_watch_test.dart'])]
 if name is not None:command+=['--plain-name',name]
 with path.open('w') as output:
  code=subprocess.run(command,cwd=ROOT,stdout=output,stderr=subprocess.STDOUT).returncode
  output.write('\nPROCESS_EXIT='+str(code)+'\n')
 return code,path.read_text()
def main():
 OUT.mkdir(parents=True,exist_ok=True);original=WATCH.read_bytes()
 for label,before,after,name in CONTROLS:assert original.decode().count(before)==1,(label,'mutation_not_unique')
 try:
  selected = CONTROLS
  if len(sys.argv) > 1:
   assert len(sys.argv) == 2 and sys.argv[1] in [control[0] for control in CONTROLS], 'invalid_resume_control'
   selected = CONTROLS[[control[0] for control in CONTROLS].index(sys.argv[1]):]
  for label,before,after,name in selected:
   assert WATCH.read_bytes()==original,'source_freeze_changed'
   candidate = original.decode().replace(before,after,1)
   if label == 'late-disposed-owner':
    # Immediate drain and mandatory queued OFF independently clean a late ON.
    # Remove both to test the complete disposal cleanup, not one redundant guard.
    assert candidate.count('    _queueOff(run);') == 1
    candidate = candidate.replace('    _queueOff(run);', '', 1)
   WATCH.write_text(candidate)
   code,log=run(OUT/('dart-agent-red-'+label+'.txt'),name)
   assert code==1 and '[E]' in log and 'Expected:' in log and 'Some tests failed.' in log,(label,'not_behavioral_red')
   print(label+': expected behavioral assertion failure',flush=True)
   WATCH.write_bytes(original)
 finally:WATCH.write_bytes(original)
 code,_=run(OUT/'dart-agent-final-restored.txt',files=['test/phone_agent_work_watch_test.dart','test/builtin_work_leases_test.dart','test/phone_server_healing_test.dart','test/phone_agent_work_provider_test.dart'])
 assert code==0,'restored_gate_failed'
 print('PASS selected behavior controls; restored helper chat/bridge/healing/provider files',flush=True)
if __name__=='__main__':main()
