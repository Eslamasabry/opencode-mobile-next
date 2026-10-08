"""BB4 actual device lease scenario, using BB9's qualified whole-session lifecycle adapter."""
import argparse
import time
import xml.etree.ElementTree as ET
from pathlib import Path
import bb9_component_update_acceptance as H

LEASE_FIELDS={'bb4WorkLeasesPassed','bb4NativeExpiryPassed','bb4IndependentOwnersPassed','bb4TerminalLifecyclePassed','bb4SetupRevocationPassed'}
def configure(host=H):
 host.STEPS['bb4WorkLeases']='bb4WorkLeasesPassed'
 host.FIELDS |= LEASE_FIELDS
 host.PHASE_NAMES |= {'instrument_bb4WorkLeases'+suffix for suffix in ['', '_identity_check', '_pre_detach', '_invoke', '_post_detach']}
 class LeaseSession:
  def __init__(self,device,args,evidence):self.device,self.evidence=device,evidence
  def run(self):
   fields=self.device.instrument('bb4WorkLeases')
   host.require(all(fields.get(key)=='true' for key in LEASE_FIELDS),'bb4_native_lease_scenario_unproven')
   self.evidence.append('PASS actual_native_expiry_setup_signin_terminal_chat_independence_and_terminal_lifecycle')
  def cleanup(self):
   # Native fixture finally drains the exact new terminal/process and deletes only its empty temporary home.
   host.require(self.device.cat(host.FIXTURE,required=False) is None,'bb4_unrelated_fixture_present')
 host.ConcurrentSession=LeaseSession
 # Before any fixture or acceptance baseline exists, Android may replace the
 # startup process. Retry that explicit Activity start once, retaining refusal.
 # Instrumentation and every subsequent acceptance phase keep their strict PID guard.
 if getattr(host.real_start, '_bb4_start_guard', False) is not True:
  base_start=host.real_start
  def stable_start(device,owner,evidence,require_explicit=False):
   try:return base_start(device,owner,evidence,require_explicit)
   except host.Q.Refused as error:
    if str(error) == 'instrumentation_replaced_app':
     evidence.append('OBSERVED initial_MainActivity_startup_identity_changed_retry_once_before_fixture')
     time.sleep(.5)
     return base_start(device,owner,evidence,require_explicit)
    if str(error) != 'normal_connected_restore_unproven':raise
    original=getattr(device,'_bb4_start_baseline',None)
    host.require(original is not None,'bb4_original_start_baseline_missing')
    values=host.Q.preference_values(original)
    host.require(values.get('owner') is not None and values['owner'].text == owner and
                 'oc.builtinRuntimeOwnership.'+owner in values,'bb4_original_start_baseline_invalid')
    evidence.append('OBSERVED initial_launch_recipe_unarmed_explicit_person_Stop_Start_before_fixture')
    # Use the immutable receipt from BEFORE upgrade/initial launch. The legacy
    # comparator requires that receipt and proves new exact kernel ownership.
    host.Q.restore_person(device,original,evidence)
    host.require(host.native_armed(device.cat(host.NATIVE),owner) and device.healthy() and
                 device.service_state(),'bb4_explicit_person_start_unproven')
  stable_start._bb4_start_guard=True
  host.real_start=stable_start

def main():
 configure()
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--emulator-go',required=True,action='store_true')
 for option in ['apk','runner-apk','apksigner','aapt','out']:p.add_argument('--'+option,type=Path,required=True)
 for option in ['target-sha','runner-sha']:p.add_argument('--'+option,required=True)
 p.add_argument('--scenario',choices=['queue'],default='queue')
 p.add_argument('--version',required=True,type=int);p.add_argument('--inherited-emulator-lock-fd',type=int)
 args=p.parse_args();evidence=[]
 try:
  H.inherited_lock(args.inherited_emulator_lock_fd)
  device=H.Device();device._bb4_start_baseline=ET.fromstring(device.cat(H.NATIVE))
  H.execute(device,args,evidence)
  evidence.append('PASS BB4_locked_native_work_leases_and_restore');return 0
 except Exception as error:
  category=type(error).__name__ if type(error) in (KeyError,AttributeError,TypeError,AssertionError,ValueError) else 'unavailable'
  evidence.append('host_failure_category='+category)
  evidence.append('FAIL '+H.safe_error(error));return 1
 finally:
  args.out.parent.mkdir(parents=True,exist_ok=True);args.out.write_text('\n'.join(evidence)+'\n')
  print('\n'.join(evidence),flush=True)
if __name__=='__main__':raise SystemExit(main())
