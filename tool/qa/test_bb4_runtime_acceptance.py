import unittest
from unittest.mock import Mock
import bb4_runtime_acceptance as B
import bb9_component_update_acceptance as H

class LeaseAdapterTest(unittest.TestCase):
 def setUp(self):
  self.old_start=H.real_start;self.old_session=H.ConcurrentSession;self.old_steps=dict(H.STEPS);self.old_fields=set(H.FIELDS);self.old_phases=set(H.PHASE_NAMES)
  B.configure(H);self.device=Mock();self.evidence=[]
 def tearDown(self):
  H.real_start=self.old_start;H.ConcurrentSession=self.old_session;H.STEPS.clear();H.STEPS.update(self.old_steps);H.FIELDS=self.old_fields;H.PHASE_NAMES=self.old_phases
 def session(self):return H.ConcurrentSession(self.device,None,self.evidence)
 def test_requires_each_actual_native_lease_lifecycle_proof(self):
  for absent in B.LEASE_FIELDS:
   self.device.instrument.return_value={key:'true' for key in B.LEASE_FIELDS-{absent}}
   with self.assertRaises(H.Q.Refused):self.session().run()
   self.assertEqual([],self.evidence)
 def test_refused_native_case_cannot_be_reported_as_pass(self):
  self.device.instrument.side_effect=H.Q.Refused('instrument_failed')
  with self.assertRaises(H.Q.Refused):self.session().run()
  self.assertEqual([],self.evidence)
 def test_actual_private_step_is_allowed_in_all_bounded_phase_receipts(self):
  for suffix in ['', '_identity_check', '_pre_detach', '_invoke', '_post_detach']:
   self.assertIn('instrument_bb4WorkLeases'+suffix,H.PHASE_NAMES)
 def test_complete_native_case_uses_exact_private_step(self):
  self.device.instrument.return_value={key:'true' for key in B.LEASE_FIELDS}
  self.session().run();self.device.instrument.assert_called_once_with('bb4WorkLeases')
  self.assertEqual(1,len(self.evidence))
 def test_does_not_accept_an_unrelated_leftover_fixture(self):
  self.device.cat.return_value='present'
  with self.assertRaises(H.Q.Refused):self.session().cleanup()
class StartupAdmissionTest(unittest.TestCase):
 def host(self, start):
  from types import SimpleNamespace
  return SimpleNamespace(STEPS={},FIELDS=set(),PHASE_NAMES=set(),real_start=start,Q=H.Q)
 def test_only_initial_identity_replacement_is_retried_once(self):
  start=Mock(side_effect=[H.Q.Refused('instrumentation_replaced_app'),None]);host=self.host(start);evidence=[]
  B.configure(host)
  from unittest.mock import patch
  with patch.object(B.time,'sleep'):host.real_start(None,'owner',evidence)
  self.assertEqual(2,start.call_count);self.assertEqual(1,len(evidence))
 def test_a_second_identity_change_remains_a_failure(self):
  start=Mock(side_effect=H.Q.Refused('instrumentation_replaced_app'));host=self.host(start);B.configure(host)
  from unittest.mock import patch
  with patch.object(B.time,'sleep'),self.assertRaises(H.Q.Refused):host.real_start(None,'owner',[])
  self.assertEqual(2,start.call_count)
 def test_no_detachment_or_native_refusal_is_retried(self):
  for reason in ['instrumentation_not_detached','canonical_owner_baseline_unproven']:
   start=Mock(side_effect=H.Q.Refused(reason));host=self.host(start);B.configure(host)
   with self.assertRaises(H.Q.Refused):host.real_start(None,'owner',[])
   self.assertEqual(1,start.call_count)
 def test_unarmed_initial_launch_uses_prior_receipt_and_requires_armed_live_server(self):
  from types import SimpleNamespace
  import xml.etree.ElementTree as ET
  original=ET.fromstring('<map><string name="owner">owner</string><string name="oc.builtinRuntimeOwnership.owner">receipt</string></map>')
  restore=Mock();start=Mock(side_effect=H.Q.Refused('normal_connected_restore_unproven'))
  host=self.host(start);host.require=H.require;host.native_armed=Mock(return_value=True);host.NATIVE='native'
  host.Q=SimpleNamespace(Refused=H.Q.Refused,preference_values=H.Q.preference_values,restore_person=restore)
  B.configure(host);device=Mock();device._bb4_start_baseline=original;device.healthy.return_value=True;device.service_state.return_value=True
  host.real_start(device,'owner',[]);restore.assert_called_once();self.assertIs(original,restore.call_args.args[1])
  host.native_armed.return_value=False
  with self.assertRaises(H.Q.Refused):host.real_start(device,'owner',[])
 def test_unarmed_initial_launch_without_prior_owner_receipt_remains_refused(self):
  from types import SimpleNamespace
  host=self.host(Mock(side_effect=H.Q.Refused('normal_connected_restore_unproven')));host.require=H.require
  restore=Mock();host.Q=SimpleNamespace(Refused=H.Q.Refused,preference_values=H.Q.preference_values,restore_person=restore)
  B.configure(host);device=SimpleNamespace(_bb4_start_baseline=None)
  with self.assertRaises(H.Q.Refused):host.real_start(device,'owner',[])
  restore.assert_not_called()
if __name__=='__main__':unittest.main()
