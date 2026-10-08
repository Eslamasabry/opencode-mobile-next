from pathlib import Path
import fcntl,json,sys,shlex
sys.path.insert(0,'/home/eslam/Storage/Code/oc_app-sol-bd')
from tool.qa.bd7_device_crash_smoke import DeviceSession,LOCK_PATH,NORMAL_APK,PACKAGE
from tool.qa.bd7_exit_proof import parse_exit_history
from tool.qa.bd9_normal_restore import prepare_restore
out=Path('build/bd7-2196-exits-only2');out.mkdir(parents=True,exist_ok=True)
class ExitOnly(DeviceSession):
 def proof(self,identity,reason,source,after,label):
  raw=self.execute(['shell','dumpsys','activity','exit-info',PACKAGE])
  entry=parse_exit_history(raw,PACKAGE,identity[0],reason)
  result={'os_exit':entry,'visible_recent_exit':False,'saved_report_qualified':False}
  try:
   self.ui.scroll_find('Recent app exits');self.ui.scroll_find('App stopped unexpectedly');self.ui.tap('App stopped unexpectedly',contains=True)
   number=self.ui.details_number('Reason code')
   if number!=reason:raise ValueError()
   result.update(visible_recent_exit=True,visible_reason_code=number)
   self.ui.screenshot(self.output/(source+'-exit.jpg'),section='Recent app exits');result['screenshot']='PASS'
  except Exception as e:
   result['ui_proof']='FAIL'
   if e.args and e.args[0] in {'ui_number_unavailable','unsafe_screenshot','navigation_target_unavailable','ui_unavailable'}:result['ui_failure_code']=e.args[0]
  return result

s=ExitOnly('adb',out)
restore=prepare_restore(NORMAL_APK,'1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C',out/'normal-restore.json')
r={'result':'FAIL','version_code':2196,'saved_report_qualified':False};stage='storage'
with LOCK_PATH.open('a') as lock:
 fcntl.flock(lock,fcntl.LOCK_EX);s.locked=True
 try:
  r['storage_before']=s.storage();stage='baseline';s.backup_diagnostics();s.launch()
  r['consent_enabled_before']=s.consent()>0;r['consent_unavailable_visible']=any("Crash reports aren't available right now. Restart the app and try again." in s.ui.text(n) for n in s.ui.nodes())
  try:s.ui.screenshot(out/'consent-unavailable.jpg',section='Crash reports');r['consent_screen_crop']='PASS'
  except Exception:r['consent_screen_crop']='FAIL'
  print('BD7 consent unavailable: '+str(r['consent_unavailable_visible']),flush=True)
  stage='native_crash';r['native_crash']=s.crash();print('BD7 native exit verified',flush=True)
  stage='anr';r['anr']=s.anr();r['result']='PARTIAL';print('BD7 ANR exit verified',flush=True)
 except Exception as e:
  r['failure_stage']=stage
  code=e.args[0] if e.args else None
  if isinstance(code,str) and code in {'exit_proof_invalid','unsafe_screenshot','ui_number_unavailable','navigation_target_unavailable','anr_dialog_unavailable','process_did_not_exit','device_command_failed','app_navigation_not_ready','main_process_unavailable','main_process_identity_invalid','signal_identity_invalid','ui_unavailable','ui_bounds_unavailable'}:r['failure_code']=code
 finally:
  try:s.restore_diagnostics();r['diagnostic_baseline_restore']='PASS'
  except Exception:r['diagnostic_baseline_restore']='FAIL'
  try:restore(s.adb);r['normal_app_restore']='PASS'
  except Exception:r['normal_app_restore']='FAIL'
  s.locked=False;(out/'report.json').write_text(json.dumps(r,indent=2)+'\n');print('BD7 exits-only result='+r['result']+' stage='+stage,flush=True)
