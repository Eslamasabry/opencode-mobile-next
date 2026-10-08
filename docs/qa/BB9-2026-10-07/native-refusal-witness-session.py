import sys,time
sys.path.insert(0,'tool/qa')
import bb9_component_update_acceptance as H
import bb9_external_installer as E
actor=None
start=E.ExternalInstaller.start
def observed_start(self,value):
 global actor
 result=start(self,value);actor=self;return result
E.ExternalInstaller.start=observed_start
instrument=H.Device._instrument
def observed_instrument(self,step,*args,**kwargs):
 try:return instrument(self,step,*args,**kwargs)
 except Exception:
  if step=='bb9ExternalCommit' and actor and actor.root_proven:
   actor.deadline=time.monotonic()+15
   try:
    root=actor._identity(actor.root['pid']);leader=actor._identity(actor.leader['pid'])
    print('native_refusal_witness_root_same_live='+str(bool(root and E.same_process(actor.root,root) and root['state'] not in {'Z','X'})).lower(),flush=True)
    print('native_refusal_witness_leader_same_live='+str(bool(leader and E.same_process(actor.leader,leader) and leader['state'] not in {'Z','X'})).lower(),flush=True)
    actor._prove_root()
    print('native_refusal_witness_original_protected_argv_uid_nonce_still_proven=true',flush=True)
    print('native_refusal_witness_update_permitted='+str(actor.permitted).lower(),flush=True)
   except Exception:print('native_refusal_witness_unavailable=true',flush=True)
  raise
H.Device._instrument=observed_instrument
H.inherited_lock()
raise SystemExit(H.main())
