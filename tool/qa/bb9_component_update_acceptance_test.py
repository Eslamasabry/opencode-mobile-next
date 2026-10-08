#!/usr/bin/env python3
"""Deterministic host-only tests. No subprocess/device calls permitted."""
import copy
import importlib.util
import json
import io
import sys
import types
from pathlib import Path
from types import SimpleNamespace as NS
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location('bb9_host', str(Path(__file__).with_name('bb9_component_update_acceptance.py')))
H = importlib.util.module_from_spec(spec); spec.loader.exec_module(H)
I = lambda pid: dict(pid=pid, startTicks=pid * 10, parent=1, group=pid, session=pid)
APP = dict(I(20), session=0)
TICKET = dict(version=1, id='a'*64, rootfsGeneration='b'*64,
    targets=['OPENCODE1','OPENCODE2','PASEO','CLAUDE','LEGACY_CLAUDE'], operation='INSTALL',
    ownership=dict(version=1, boot='12345678-1234-1234-1234-123456789abc', nonce='c'*64,
                   generation=1, root=I(30), leader=I(31), other=[APP]), observed=[I(30), I(31), I(32)])
FIX = dict(version=1, stage='armed', app=APP, originalExecutableSha256='d'*64,
           originalGoodPresent=False, originalLockPresent=False, ticket=TICKET, activityAbsent=True)

def writer(ticket=TICKET):
    r=ET.Element('map'); ET.SubElement(r,'string',name='rootfsGeneration').text=ticket['rootfsGeneration']
    ET.SubElement(r,'string',name='ticket').text=json.dumps(ticket)
    return ET.tostring(r,encoding='unicode')

class Mock:
    def __init__(self):
        self.events=[]; self.app=dict(APP,state='S'); self.records={i['pid']:dict(i,state='S') for i in H.members(FIX)}
        self.files={H.FIXTURE:json.dumps(FIX),H.WRITER:writer(),H.NATIVE:'<map />'}
        self.absent=True; self.detached_state=True; self.cold=False; self.mutate_writer=False
    def instrument(self,step,**unused):
        self.events.append(step)
        if step=='bb9Verify':
            H.require(self.cold,'mock_verify_before_cold')
        return {H.STEPS[step]:'true','bb9OwnedWriterGone':'true'}
    def cat(self,path,required=True): return self.files.get(path)
    def app_identity(self): return self.app
    def identity(self,pid): return self.records.get(pid) if pid!=20 else self.app
    def activity_absent(self): return self.absent
    def wait_detached(self,app):
        self.events.append('detach'); H.require(self.detached_state,'mock_not_detached')
    def detached(self,app): return self.detached_state
    def signal(self,app,sig):
        self.events.append(('signal',app['pid'],sig)); self.app=None
    def write_dead(self,path,raw):
        H.require(self.app is None,'mock_write_while_live');self.events.append('write_dead');self.files[path]=raw
        if self.mutate_writer: self.files[H.WRITER]='<map />'
    def recovery_complete(self,digest): self.events.append('recovery_proof');return self.cold and digest=='d'*64
    def ensure_normal_app(self): self.app=dict(I(40),state='S');return self.app
    def cold_start(self,*args):
        self.events.append('normal_Start');self.cold=True;self.app=dict(I(40),state='S');self.records={};self.files[H.WRITER]='<map />'

class SessionTests(unittest.TestCase):
    def run_session(self,d=None):
        d=d or Mock(); evidence=[]; s=H.Session(d,'<map original="true"/>','owner',evidence)
        with patch.object(H,'real_start',side_effect=d.cold_start):s.run()
        return d,evidence,s
    def test_actual_normal_start_precedes_read_only_verify(self):
        d,e,_=self.run_session()
        self.assertLess(d.events.index('normal_Start'),d.events.index('recovery_proof'))
        self.assertLess(d.events.index('recovery_proof'),d.events.index('bb9Verify'))
        self.assertEqual([x for x in d.events if isinstance(x,tuple)],[('signal',20,'KILL')])
        self.assertLess(d.events.index('detach'),d.events.index(('signal',20,'KILL')))
        self.assertLess(d.events.index('write_dead'),d.events.index('normal_Start'))
    def test_detached_is_mandatory_before_signal(self):
        d=Mock();d.detached_state=False
        with self.assertRaises(H.Q.Refused):self.run_session(d)
        self.assertFalse(any(isinstance(e,tuple) for e in d.events))
    def test_activity_absent_is_mandatory(self):
        d=Mock();d.absent=False
        with self.assertRaises(H.Q.Refused):self.run_session(d)
        self.assertFalse(any(isinstance(e,tuple) for e in d.events))
    def test_app_pid_reuse_refuses_signal(self):
        d=Mock();d.app['startTicks']+=1
        with self.assertRaises(H.Q.Refused):self.run_session(d)
        self.assertFalse(any(isinstance(e,tuple) for e in d.events))
    def test_saved_writer_ticket_must_match(self):
        d=Mock();t=copy.deepcopy(TICKET);t['id']='e'*64;d.files[H.WRITER]=writer(t)
        with self.assertRaises(H.Q.Refused):self.run_session(d)
        self.assertFalse(any(isinstance(e,tuple) for e in d.events))
    def test_additive_generation_must_match_and_is_not_restored(self):
        d=Mock();d.files[H.WRITER]=writer().replace('b'*64,'f'*64)
        with self.assertRaises(H.Q.Refused):self.run_session(d)
        self.assertFalse(any(isinstance(e,tuple) for e in d.events))
    def test_host_metadata_write_cannot_change_journal(self):
        d=Mock();d.mutate_writer=True
        with self.assertRaisesRegex(H.Q.Refused,'writer_journal_changed'):self.run_session(d)
        self.assertNotIn('normal_Start',d.events)
    def test_instrumentation_cannot_supply_cold_proof(self):
        d=Mock();d.cold_start=lambda *a:d.events.append('normal_Start')
        with self.assertRaisesRegex(H.Q.Refused,'actual_cold_start_recovery'):self.run_session(d)
        self.assertNotIn('bb9Verify',d.events)
    def test_survivors_reported_without_orphan_claim(self):
        _,e,_=self.run_session();self.assertIn('observed_writer_survivors=3',e)
        self.assertFalse(any('orphan' in line for line in e))
    def test_natural_death_reported_without_drain_claim(self):
        d=Mock();d.records={};_,e,_=self.run_session(d)
        self.assertIn('observed_writer_survivors=0',e);self.assertIn('writer_identities_no_longer_live=3',e)
        self.assertFalse(any('orphan' in line for line in e))
    def test_remaining_writer_refuses_verify(self):
        d=Mock()
        def start(*unused):d.cold=True;d.events.append('normal_Start')
        d.cold_start=start
        with self.assertRaisesRegex(H.Q.Refused,'writer_not_drained'):self.run_session(d)
        self.assertNotIn('bb9Verify',d.events)

class ExternalSessionTests(unittest.TestCase):
    def test_natural_installer_death_cannot_qualify_orphan(self):
        d=Mock();d.records={};e=[];session=H.Session(d,'<map/>','owner',e);session.saved=FIX
        with patch.object(H,'real_start',side_effect=d.cold_start), self.assertRaisesRegex(H.Q.Refused,'orphan_installer_survival_unproven'):
            session.cold_cycle(require_orphan=True)
        self.assertNotIn('normal_Start',d.events)
        self.assertFalse(any('PASS actual_original_installer' in line for line in e))
    def test_original_root_and_leader_required_even_when_child_survives(self):
        d=Mock();d.records={32:d.records[32]};e=[];session=H.Session(d,'<map/>','owner',e);session.saved=FIX
        with patch.object(H,'real_start',side_effect=d.cold_start), self.assertRaisesRegex(H.Q.Refused,'orphan_installer_survival_unproven'):
            session.cold_cycle(require_orphan=True)
        self.assertNotIn('normal_Start',d.events)
    def test_observed_orphan_proof_still_requires_real_product_start(self):
        d=Mock();e=[];session=H.Session(d,'<map/>','owner',e);session.saved=FIX
        with patch.object(H,'real_start',side_effect=d.cold_start): session.cold_cycle(require_orphan=True)
        self.assertIn('PASS actual_original_installer_root_and_leader_survived_app_SIGKILL',e)
        self.assertLess(d.events.index('normal_Start'),d.events.index('bb9Verify'))
    def reader_session(self, proof=True, drained=True, verified=True):
        d=Mock(); calls=[]; session=H.Session(d,'<map/>','owner',[]);session.saved=FIX
        session.external=NS(acknowledge_recovered_app=lambda *args:calls.append(args),
                            acknowledge_native_drain=lambda *args:calls.append(('nativeDrain',*args)))
        original=d.instrument
        def verify(step,**kwargs):
            result=original(step,**kwargs)
            if step=='bb9Verify':
                result.update(bb9VerifierAppPid='40' if proof else '41',bb9VerifierAppStartTicks='400',
                              bb9OwnedWriterGone='true' if drained else 'false',bb9Verified='true' if verified else 'false')
            return result
        d.instrument=verify
        return d,session,calls
    def test_recovered_reader_ack_follows_real_native_verified_recovery(self):
        d,s,calls=self.reader_session()
        with patch.object(H,'real_start',side_effect=d.cold_start):s.cold_cycle(require_orphan=True)
        self.assertEqual(calls,[(d.app,40,400),('nativeDrain',True,True)])
        self.assertLess(d.events.index('recovery_proof'),d.events.index('bb9Verify'))
    def test_recovered_reader_ack_refuses_unmatched_native_identity(self):
        d,s,calls=self.reader_session(proof=False)
        with patch.object(H,'real_start',side_effect=d.cold_start),self.assertRaisesRegex(H.Q.Refused,'native_recovered_reader_unproven'):
            s.cold_cycle(require_orphan=True)
        self.assertEqual(calls,[])
    def test_native_drain_ack_requires_actual_verified_recovery(self):
        d,s,calls=self.reader_session(verified=False)
        with patch.object(H,'real_start',side_effect=d.cold_start),self.assertRaisesRegex(H.Q.Refused,'native_recovery_verify_unproven'):
            s.cold_cycle(require_orphan=True)
        self.assertEqual(calls,[])
    def test_native_drain_ack_must_be_present_after_reader_ack(self):
        d,s,calls=self.reader_session()
        del s.external.acknowledge_native_drain
        with patch.object(H,'real_start',side_effect=d.cold_start),self.assertRaisesRegex(H.Q.Refused,'native_drain_ack_unavailable'):
            s.cold_cycle(require_orphan=True)
        self.assertEqual(calls,[(d.app,40,400)])
    def test_recovered_reader_ack_requires_native_writer_gone(self):
        d,s,calls=self.reader_session(drained=False)
        with patch.object(H,'real_start',side_effect=d.cold_start),self.assertRaisesRegex(H.Q.Refused,'native_writer_drain_unproven'):
            s.cold_cycle(require_orphan=True)
        self.assertEqual(calls,[])
    def test_external_drain_precedes_native_fixture_cleanup(self):
        d=Mock();session=H.ExternalSession(d,'<map/>','owner',[]);session.prepared=True
        session.external=NS(drain=lambda:d.events.append('external_drain'))
        session.cleanup()
        self.assertLess(d.events.index('external_drain'),d.events.index('bb9Cleanup'))
    def test_external_drain_refusal_still_attempts_independent_native_cleanup(self):
        d=Mock();session=H.ExternalSession(d,'<map/>','owner',[]);session.prepared=True
        def refuse():raise H.Q.Refused('host_drain_unproven')
        session.external=NS(drain=refuse)
        with self.assertRaisesRegex(H.Q.Refused,'host_drain_unproven'):session.cleanup()
        self.assertIn('bb9Cleanup',d.events)
    def test_native_commit_refusal_never_permits_update(self):
        d=Mock();d.files[H.EXTERNAL_EXPORT]='{}';calls=[]
        actor=NS(export={'ticketId':'a'*64},start=lambda unused:{'root':I(30),'leader':I(31)},
                 mark_committed=lambda unused:calls.append('commit'),permit=lambda:calls.append('permit'))
        module=types.ModuleType('bb9_external_installer');module.ExternalInstaller=lambda unused:actor
        original=d.instrument
        def instrument(step,*args,**kwargs):
            if step=='bb9ExternalCommit':raise H.Q.Refused('native_ownership_refused')
            return original(step,**kwargs)
        d.instrument=instrument
        with patch.dict(sys.modules,{'bb9_external_installer':module}), self.assertRaisesRegex(H.Q.Refused,'native_ownership_refused'):
            H.ExternalSession(d,'<map/>','owner',[]).run()
        self.assertEqual(calls,[])


class CommandLinkTests(unittest.TestCase):
    def test_healthy_executable_requires_agreeing_command_link(self):
        class Device(H.Device):
            def executable_hash(self):return 'd'*64
            def adb(self,*args,**kwargs):
                return NS(returncode=0,stdout='/opt/another-program/bin/opencode2\n' if 'readlink' in args else '')
            def cat(self,*unused,**kwargs):return '<map/>'
        self.assertFalse(Device().recovery_complete('d'*64))
    def test_guest_link_and_program_journal_must_both_agree(self):
        class Device(H.Device):
            def executable_hash(self):return 'd'*64
            def adb(self,*args,**kwargs):return NS(returncode=0,stdout='/opt/opencode2/bin/opencode2\n' if 'readlink' in args else '')
            def cat(self,*unused,**kwargs):return '<map/>'
        self.assertTrue(Device().recovery_complete('d'*64))


class SchemaTests(unittest.TestCase):
    def test_zero_session_app_and_duplicate_same_identity_allowed(self):self.assertEqual(H.fixture(json.dumps(FIX))['app']['session'],0)
    def test_prior_good_retention(self):
        f=copy.deepcopy(FIX);f.update(originalGoodPresent=True,originalGoodDevice=1,originalGoodInode=42)
        self.assertEqual(H.fixture(json.dumps(f))['originalGoodInode'],42)
    def test_malformed_rejected(self):
        for mutate in [lambda f:f.update(version=True),lambda f:f['app'].update(session=-1),
                       lambda f:f.update(activityAbsent=False),lambda f:f.update(originalExecutableSha256='bad'),
                       lambda f:f['ticket'].update(operation='CHECK'),lambda f:f['ticket']['ownership']['leader'].update(session=0),
                       lambda f:f['ticket']['observed'][0].update(startTicks=99),lambda f:f['ticket'].update(observed=[]),
                       lambda f:f['ticket'].update(targets=['OPENCODE2']),lambda f:f.update(originalGoodPresent=True)]:
            f=copy.deepcopy(FIX);mutate(f)
            with self.subTest(f=f),self.assertRaises(H.Q.Refused):H.fixture(json.dumps(f))
    def test_output_drops_untrusted_secret_fields(self):
        raw='INSTRUMENTATION_STATUS: providerKey=secret\nINSTRUMENTATION_STATUS: bb9Verified=true\nINSTRUMENTATION_STATUS: error=raw_private_error'
        self.assertEqual(H.parse_status(raw),{'bb9Verified':'true'})
    def test_six_boolean_fields_admitted(self):
        for key in set(H.STEPS.values())|{'bb9OriginalGoodPresent','bb9OwnedWriterGone'}:
            self.assertEqual(H.parse_status('INSTRUMENTATION_STATUS: '+key+'=true'),{key:'true'})
    def test_arbitrary_exception_text_redacted(self):self.assertEqual(H.safe_error(ValueError('secret')),'host_acceptance_unavailable')
    def test_invalid_safe_code_redacted(self):self.assertEqual(H.safe_error(H.Q.Refused('secret value')),'host_acceptance_unavailable')
    def test_ambiguous_managed_profile_refused(self):
        p=dict(id='one',flavor='v2',baseUrl='http://127.0.0.1:4097',backend='openCode')
        tree=ET.Element('map');ET.SubElement(tree,'string',name='flutter.oc.profiles').text=json.dumps([p,p])
        with self.assertRaises(H.Q.Refused):H.selected_profile(ET.tostring(tree,encoding='unicode'))

class BootstrapTests(unittest.TestCase):
    def test_same_package_private_alias_qualified(self):
        launcher='/data/app/abc/lib/x86_64/libproot.so'
        for root in [H.UBUNTU, '/data/data/' + H.PACKAGE + '/files/linux/ubuntu']:
            self.assertTrue(H.qualified_payload_root([launcher,'--rootfs='+root], '', 20))
        self.assertFalse(H.qualified_payload_root([launcher,'--rootfs=/data/user/0/other/files/linux/ubuntu'], '', 20))
    def test_mapped_private_orphan_requires_parent_one_and_exact_executable(self):
        path='/data/data/' + H.PACKAGE + '/files/linux/ubuntu/opt/opencode2/bin/opencode2'
        maps='7000-8000 r-xp 00000000 00:01 2 '+path+' (deleted)\n'
        self.assertTrue(H.qualified_payload_root(['opencode2'],maps,1))
        self.assertFalse(H.qualified_payload_root(['opencode2'],maps,20))
        self.assertFalse(H.qualified_payload_root(['opencode2'],maps.replace('/opt/opencode2/bin/opencode2','/tmp/opencode2'),1))
        self.assertFalse(H.qualified_payload_root(['opencode2'],maps.replace('r-xp','rw-p'),1))

    def test_inventory_rejects_other_format(self):
        d=NS(adb=lambda *a,**k:NS(returncode=0,stdout='PID PPID UID\n7 1 u0_a217\n'))
        with self.assertRaises(H.Q.Refused):H.uid_inventory(d,10217)
    def test_inventory_uid_filter_and_cap(self):
        d=NS(adb=lambda *a,**k:NS(returncode=0,stdout='PID PPID UID\n7 1 10217\n8 1 0\n'))
        self.assertEqual(H.uid_inventory(d,10217),{7:1})
        d.adb=lambda *a,**k:NS(returncode=0,stdout='PID PPID UID\n'+''.join(f'{i} 1 10217\n' for i in range(2,131)))
        with self.assertRaises(H.Q.Refused):H.uid_inventory(d,10217)
    def test_unknown_payload_prevents_any_stop_install_signal(self):
        events=[]
        d=NS(adb=lambda *a,**k:(events.append(a) or NS(returncode=0,stdout='package:'+H.PACKAGE+' uid:10217\n',stderr='')),
             app_identity=lambda:None, identity=lambda p:dict(I(p),state='S'),cat=lambda p,**k:'unknown\x00')
        with patch.object(H,'uid_inventory',return_value={30:1}),self.assertRaisesRegex(H.Q.Refused,'unknown_uid_payload'):
            H.bootstrap(d,NS(),[])
        self.assertFalse(any('install' in e or 'force-stop' in e for e in events))

class ArtifactTests(unittest.TestCase):
    def test_real_gradle_runner_has_no_version_but_target_is_exact(self):
        import tempfile,hashlib
        with tempfile.TemporaryDirectory() as temporary:
            target=Path(temporary)/'target.apk';runner=Path(temporary)/'runner.apk'
            target.write_bytes(b'fixture');runner.write_bytes(b'fixture')
            digest=hashlib.sha256(b'fixture').hexdigest()
            args=NS(apk=target,runner_apk=runner,target_sha=digest,runner_sha=digest,version=2203,apksigner='signer',aapt='aapt')
            def run(cmd,**unused):
                if cmd[0]=='signer':return NS(returncode=0,stdout='Signer #1 certificate SHA-256 digest: '+H.Q.CERT)
                is_target=cmd[-1]==str(target)
                return NS(returncode=0,stdout="package: name='"+H.PACKAGE+('' if is_target else '.test')+"' versionCode='"+('2203' if is_target else '')+"' versionName=''\n")
            device=NS(run=run,adb=lambda *args,**unused:NS(returncode=0,stdout='versionCode=2196'))
            H.validate_candidates(device,args)
            args.version=2204
            with self.assertRaisesRegex(H.Q.Refused,'candidate_metadata_mismatch'):H.validate_candidates(device,args)

class RestoreTests(unittest.TestCase):
    def test_policy_values_ignore_xml_layout_and_refuse_value_changes(self):
        left=ET.fromstring('<map><string name="policy">fixture</string>\n    </map>')[0]
        right=ET.fromstring('<map><string name="policy">fixture</string>\n</map>')[0]
        self.assertTrue(H.preference_equal(left,right))
        right.text='changed'
        self.assertFalse(H.preference_equal(left,right))
        self.assertFalse(H.preference_equal(left,None))

    def test_cleanup_refusal_does_not_skip_person_restore(self):
        original='<map><string name="owner">owner</string><string name="flutter.oc.activeProfile">owner</string></map>'
        d=NS(adb=lambda *a,**k:NS(stdout='device'),cat=lambda path:original,
             installed_hash=lambda package:'a'*64, healthy=lambda:True,service_state=lambda:True,
             instrument=lambda s:{'installedCertificateSha256':H.Q.CERT,'installedVersion':'2203','installedRuntimeQa':'true','baselinePolicyMarkerValid':'true'})
        args=NS(target_sha='a'*64,runner_sha='a'*64,version=2203)
        fake=NS(run=lambda:None,cleanup=lambda:(_ for _ in ()).throw(H.Q.Refused('cleanup_refused')))
        restored=[]
        with patch.object(H,'validate_candidates'),patch.object(H,'selected_profile',return_value='owner'),patch.object(H,'real_start'),\
             patch.object(H,'native_armed',return_value=True),patch.object(H.Q,'baseline_policy_valid',return_value=True),\
             patch.object(H,'Session',return_value=fake),patch.object(H.Q,'restore_person',side_effect=lambda *a:restored.append(True)):
            with self.assertRaisesRegex(H.Q.Refused,'cleanup_refused'):H.execute(d,args,[])
        self.assertEqual(restored,[True])

class FilesystemTests(unittest.TestCase):
    def test_partial_native_cases_cannot_claim_filesystem_acceptance(self):
        fields = {key: 'true' for key in H.FILESYSTEM_FIELDS}
        fields.pop('bb9UnknownQuiescenceRefused')
        session = H.FilesystemSession(NS(instrument=lambda step: fields), NS(), [])
        with self.assertRaisesRegex(H.Q.Refused, 'filesystem_cases_unproven'):
            session.run()

    def test_failed_prepare_keeps_native_cleanup_obligation(self):
        calls = []
        def instrument(step):
            calls.append(step)
            if step == 'bb9FilesystemCases':
                raise H.Q.Refused('fixture_prepare_refused')
            return {'bb9FilesystemCleanupComplete': 'true'}
        device = NS(instrument=instrument, ensure_normal_app=lambda: calls.append('normal_app'))
        session = H.FilesystemSession(device, NS(), [])
        with self.assertRaisesRegex(H.Q.Refused, 'fixture_prepare_refused'):
            session.run()
        session.cleanup()
        self.assertEqual(calls, ['bb9FilesystemCases', 'normal_app', 'bb9FilesystemCleanup'])

    def test_native_cleanup_refusal_is_not_a_cleanup_pass(self):
        device = NS(ensure_normal_app=lambda: None,
                    instrument=lambda step: (_ for _ in ()).throw(H.Q.Refused('cleanup_refused')))
        evidence = []
        session = H.FilesystemSession(device, NS(), evidence)
        session.prepared = True
        with self.assertRaisesRegex(H.Q.Refused, 'cleanup_refused'):
            session.cleanup()
        self.assertEqual(evidence, [])

class PhaseTests(unittest.TestCase):
    def device(self, failure_at=None, native_failure=False):
        d=H.Device();d._phase_evidence=[];d._phase_count=0;calls=[]
        d.app_identity=lambda:dict(APP,state='S')
        def detached(app):
            calls.append('detach')
            if len(calls)==failure_at:
                raise H.Q.Refused('instrumentation_replaced_app')
        d.wait_detached=detached
        raw='INSTRUMENTATION_STATUS: builtinRuntimeResult='+('FAIL' if native_failure else 'PASS')+'\n'
        raw+='INSTRUMENTATION_STATUS: builtinRuntimeFailure=bb9_serialized_checks_failed\n' if native_failure else ''
        raw+='INSTRUMENTATION_STATUS: bb9PreflightPassed=true\nINSTRUMENTATION_CODE: -1\n'
        d.adb=lambda *a,**k:(calls.append('invoke') or NS(returncode=0,stdout=raw))
        return d,calls
    def test_pre_detach_failure_identifies_step_and_never_invokes(self):
        d,calls=self.device(failure_at=1)
        with self.assertRaisesRegex(H.Q.Refused,'instrumentation_replaced_app'):d.instrument('bb3Preflight')
        self.assertEqual(calls,['detach'])
        self.assertIn('phase=instrument_bb3Preflight_pre_detach outcome=failure category=identity_changed',d._phase_evidence)
        self.assertNotIn('phase=instrument_bb3Preflight_invoke outcome=before',d._phase_evidence)
    def test_post_detach_failure_keeps_hard_guard(self):
        d,calls=self.device(failure_at=3)
        with self.assertRaisesRegex(H.Q.Refused,'instrumentation_replaced_app'):d.instrument('bb9Preflight')
        self.assertEqual(calls,['detach','invoke','detach'])
        self.assertIn('phase=instrument_bb9Preflight_invoke outcome=after',d._phase_evidence)
        self.assertIn('phase=instrument_bb9Preflight_post_detach outcome=failure category=identity_changed',d._phase_evidence)
        self.assertNotIn('phase=instrument_bb9Preflight outcome=after',d._phase_evidence)
    def test_expected_native_failure_still_requires_same_post_identity(self):
        d,_=self.device(failure_at=3,native_failure=True)
        with self.assertRaisesRegex(H.Q.Refused,'instrumentation_replaced_app'):
            d.instrument('bb9SerializedChecks',expected_failure='bb9_serialized_checks_failed')
        self.assertIn('phase=instrument_bb9SerializedChecks_post_detach outcome=failure category=identity_changed',d._phase_evidence)
    def test_success_has_bounded_before_after_for_each_phase(self):
        d,_=self.device();d.instrument('bb9Preflight')
        for suffix in ['', '_identity_check','_pre_detach','_invoke','_post_detach']:
            for outcome in ['before','after']:
                self.assertIn('phase=instrument_bb9Preflight'+suffix+' outcome='+outcome,d._phase_evidence)
    def test_phase_privacy_and_budget(self):
        d=NS(_phase_evidence=[],_phase_count=0)
        with self.assertRaises(ValueError):
            H.traced(d,'real_start',lambda:(_ for _ in ()).throw(ValueError('private_account_secret')))
        self.assertEqual(d._phase_evidence, ['phase=real_start outcome=before','phase=real_start outcome=failure category=invalid_data'])
        for _ in range(300):H.phase_record(d,'real_start','before')
        self.assertEqual(len(d._phase_evidence),256)
        self.assertNotIn('private_account_secret',' '.join(d._phase_evidence))
    def test_real_start_failure_is_distinct_from_preflight(self):
        original='<map><string name="owner">owner</string><string name="flutter.oc.activeProfile">owner</string></map>'
        d=NS(adb=lambda *a,**k:NS(stdout='device'),cat=lambda path:original,installed_hash=lambda p:'a'*64)
        args=NS(target_sha='a'*64,runner_sha='a'*64,version=2203);evidence=[]
        with patch.object(H,'validate_candidates'),patch.object(H,'selected_profile',return_value='owner'),\
             patch.object(H,'real_start',side_effect=H.Q.Refused('instrumentation_replaced_app')),patch.object(H.Q,'restore_person'):
            with self.assertRaisesRegex(H.Q.Refused,'instrumentation_replaced_app'):H.execute(d,args,evidence)
        self.assertIn('phase=real_start outcome=failure category=identity_changed',evidence)
        self.assertNotIn('phase=instrument_bb3Preflight outcome=before',evidence)
        self.assertIn('phase=person_restore outcome=after',evidence)


class FinalMetadataHandoffTests(unittest.TestCase):
    def case(self, capability='restore'):
        original='<map><string name="flutter.oc.activeProfile">owner</string><string name="flutter.oc.automation.owner">original</string></map>'
        changed=original.replace('>original<', '>migrated<')
        data=[original];events=[]
        d=NS(adb=lambda *a,**k:NS(stdout='device'),cat=lambda p: data[0] if p==H.FLUTTER else '<map/>',
             installed_hash=lambda p:'a'*64,healthy=lambda:True,service_state=lambda:True,
             instrument=lambda *a,**k:dict(installedCertificateSha256=H.Q.CERT,installedVersion='2211',
                                         installedRuntimeQa='true',baselinePolicyMarkerValid='true'))
        def restore_metadata():
            events.append('metadata')
            if capability=='refuse':raise H.Q.Refused('metadata_uid_not_quiescent')
            data[0]=original
        if capability!='absent':d.restore_original_metadata_before_comparison=restore_metadata if capability!='invalid' else True
        fake=NS(run=lambda:data.__setitem__(0,changed),cleanup=lambda:events.append('cleanup'))
        args=NS(target_sha='a'*64,runner_sha='a'*64,version=2211);evidence=[]
        def invoke():
            with patch.object(H,'validate_candidates'),patch.object(H,'selected_profile',return_value='owner'),                 patch.object(H,'real_start'),patch.object(H,'native_armed',return_value=True),                 patch.object(H.Q,'baseline_policy_valid',return_value=True),patch.object(H,'Session',return_value=fake),                 patch.object(H.Q,'restore_person',side_effect=lambda *a:events.append('person')):
                H.execute(d,args,evidence)
        return invoke,events,evidence
    def test_typed_comparison_follows_actual_metadata_restore(self):
        invoke,events,evidence=self.case();invoke()
        self.assertEqual(events,['cleanup','person','metadata'])
        self.assertIn('PASS original_policy_marker_metadata_and_same_installed_app_preserved',evidence)
    def test_no_capability_still_refuses_changed_metadata(self):
        invoke,_,evidence=self.case('absent')
        with self.assertRaisesRegex(H.Q.Refused,'restored_policy_metadata_changed'):invoke()
        self.assertNotIn('PASS original_policy_marker_metadata_and_same_installed_app_preserved',evidence)
    def test_capability_failure_cannot_claim_restoration(self):
        invoke,_,evidence=self.case('refuse')
        with self.assertRaisesRegex(H.Q.Refused,'metadata_uid_not_quiescent'):invoke()
        self.assertNotIn('PASS original_policy_marker_metadata_and_same_installed_app_preserved',evidence)
    def test_invalid_capability_refuses(self):
        invoke,_,_=self.case('invalid')
        with self.assertRaisesRegex(H.Q.Refused,'final_metadata_restore_unavailable'):invoke()


class EarlyDiagnosisTests(unittest.TestCase):
    def failed_start(self, app=dict(APP,state='S'), diagnosis_error=None):
        original='<map><string name="owner">owner</string><string name="flutter.oc.activeProfile">owner</string></map>'
        calls=[]
        def instrument(step,**kwargs):
            calls.append((step,kwargs))
            if diagnosis_error:
                raise diagnosis_error
            return {'bb3RestorePhase':'unavailable','bb3SecretCredential':'private_secret',
                    'bb3RestoreReason':'private account value'}
        d=NS(adb=lambda *a,**k:NS(stdout='device'),cat=lambda p,**k:writer() if p==H.WRITER else original,
             installed_hash=lambda p:'a'*64,app_identity=lambda:app,instrument=instrument)
        args=NS(target_sha='a'*64,runner_sha='a'*64,version=2203);evidence=[];restored=[]
        with patch.object(H,'validate_candidates'),patch.object(H,'selected_profile',return_value='owner'),\
             patch.object(H,'real_start',side_effect=H.Q.Refused('normal_connected_restore_unproven')),\
             patch.object(H.Q,'restore_person',side_effect=lambda *a:restored.append(True)):
            with self.assertRaisesRegex(H.Q.Refused,'normal_connected_restore_unproven'):H.execute(d,args,evidence)
        self.assertEqual(restored,[True])
        return calls,evidence
    def test_start_failure_without_session_gets_read_only_exact_app_diagnosis(self):
        calls,evidence=self.failed_start()
        self.assertEqual(calls,[('bb3Capabilities',{'expected_app':dict(APP,state='S')})])
        capabilities=[line for line in evidence if line.startswith('native_capabilities_after_refusal=')]
        self.assertEqual(len(capabilities),1)
        self.assertEqual(json.loads(capabilities[0].split('=',1)[1]),{'bb3RestorePhase':'unavailable'})
        projection=[line for line in evidence if line.startswith('writer_after_refusal=')]
        self.assertEqual(json.loads(projection[0].split('=',1)[1]),{'present':True,'sameFixture':False,'operation':'INSTALL'})
        self.assertNotIn('private_secret',' '.join(evidence));self.assertNotIn('a'*64,' '.join(evidence))
    def test_absent_app_skips_instrumentation(self):
        calls,evidence=self.failed_start(app=None)
        self.assertEqual(calls,[]);self.assertIn('native_diagnosis_app_absent=true',evidence)
    def test_diagnosis_identity_refusal_keeps_primary_and_restoration(self):
        _,evidence=self.failed_start(diagnosis_error=H.Q.Refused('instrumentation_replaced_app'))
        self.assertIn('native_diagnosis_unavailable=true',evidence)
    def test_phase_print_is_fixed_and_flushed(self):
        d=NS(_phase_evidence=[],_phase_count=0)
        with patch('builtins.print') as printed:
            H.phase_record(d,'real_start','before')
        printed.assert_called_once_with('phase=real_start outcome=before',flush=True)
        self.assertEqual(d._phase_evidence,['phase=real_start outcome=before'])


def red_proof():
    global H
    restored = H
    source = Path(str(Path(__file__).with_name('bb9_component_update_acceptance.py'))).read_text()
    mutations = [
        ('actual_final_metadata_restore', "                    restore_metadata()", "                    pass", FinalMetadataHandoffTests, 'test_typed_comparison_follows_actual_metadata_restore'),
        ('native_verify_drain_ack', "require(result.get('bb9Verified') == 'true', 'native_recovery_verify_unproven')", "pass", ExternalSessionTests, 'test_native_drain_ack_requires_actual_verified_recovery'),
        ('native_drain_ack_callable', "require(callable(confirm), 'native_drain_ack_unavailable')", "pass", ExternalSessionTests, 'test_native_drain_ack_must_be_present_after_reader_ack'),
        ('native_reader_exact_ack', "require(current is not None and result.get('bb9VerifierAppPid') == str(current['pid']) and\n                    result.get('bb9VerifierAppStartTicks') == str(current['startTicks']),\n                    'native_recovered_reader_unproven')", "pass", ExternalSessionTests, 'test_recovered_reader_ack_refuses_unmatched_native_identity'),
        ('native_reader_drain_ack', "require(result.get('bb9OwnedWriterGone') == 'true', 'native_writer_drain_unproven')", "pass", ExternalSessionTests, 'test_recovered_reader_ack_requires_native_writer_gone'),
        ('native_cleanup_after_external_refusal', "drain_error = error\n        # Native cleanup", "raise\n        # Native cleanup", ExternalSessionTests, 'test_external_drain_refusal_still_attempts_independent_native_cleanup'),
        ('command_link_agrees', "if link.returncode != 0 or link.stdout.strip() not in expected:", "if False:", CommandLinkTests, 'test_healthy_executable_requires_agreeing_command_link'),
        ('orphan_survival', "all(Q.same_process(i, d.identity(i['pid'])) for i in [receipt['root'], receipt['leader']])", "True", ExternalSessionTests, 'test_natural_installer_death_cannot_qualify_orphan'),
        ('native_commit_before_permit', "d.instrument('bb9ExternalCommit', *extra)", "pass", ExternalSessionTests, 'test_native_commit_refusal_never_permits_update'),
        ('filesystem_partial_proof', "require(all(result.get(field) == 'true' for field in FILESYSTEM_FIELDS),\n                'filesystem_cases_unproven')", "pass", FilesystemTests, 'test_partial_native_cases_cannot_claim_filesystem_acceptance'),
        ('filesystem_partial_cleanup', 'if self.prepared:\n            self.device.ensure_normal_app()', 'if False:\n            self.device.ensure_normal_app()', FilesystemTests, 'test_failed_prepare_keeps_native_cleanup_obligation'),
        ('early_read_only_diagnosis', "if app is not None:\n                # Read-only native projection", "if session and app is not None:\n                # Read-only native projection", EarlyDiagnosisTests, 'test_start_failure_without_session_gets_read_only_exact_app_diagnosis'),
        ('live_phase_flush', "print(line, flush=True)", "pass", EarlyDiagnosisTests, 'test_phase_print_is_fixed_and_flushed'),
        ('pre_detach_phase', "traced(self, 'instrument_' + step + '_pre_detach', lambda: self.wait_detached(app))", "self.wait_detached(app)", PhaseTests, 'test_pre_detach_failure_identifies_step_and_never_invokes'),
        ('post_detach_phase', "traced(self, 'instrument_' + step + '_post_detach', lambda: self.wait_detached(app))", "self.wait_detached(app)", PhaseTests, 'test_post_detach_failure_keeps_hard_guard'),
        ('real_start_phase', "traced(device, 'real_start', lambda: real_start(device, owner, evidence))", "real_start(device, owner, evidence)", PhaseTests, 'test_real_start_failure_is_distinct_from_preflight'),
        ('phase_budget', "if count >= 256:", "if False:", PhaseTests, 'test_phase_privacy_and_budget'),
        ('private_alias', "private_roots = [UBUNTU, '/data/data/' + PACKAGE + '/files/linux/ubuntu']", "private_roots = [UBUNTU]", BootstrapTests, 'test_same_package_private_alias_qualified'),
        ('mapped_orphan', "return proot or mapped", "return proot", BootstrapTests, 'test_mapped_private_orphan_requires_parent_one_and_exact_executable'),
        ('activity_absence', "and d.activity_absent()", "", SessionTests, 'test_activity_absent_is_mandatory'),
        ('pid_identity', "Q.same_process(app, d.app_identity())", "True", SessionTests, 'test_app_pid_reuse_refuses_signal'),
        ('writer_ticket', "json.loads(writer_values['ticket'].text) == self.saved['ticket']", "True", SessionTests, 'test_saved_writer_ticket_must_match'),
        ('journal_preservation', "require(d.cat(WRITER) == writer and d.cat(FIXTURE) == private_fixture, 'writer_journal_changed_by_host')", "pass", SessionTests, 'test_host_metadata_write_cannot_change_journal'),
        ('actual_cold_recovery', "require(d.recovery_complete(self.saved['originalExecutableSha256']), 'actual_cold_start_recovery_unproven')", "pass", SessionTests, 'test_instrumentation_cannot_supply_cold_proof'),
        ('writer_gone_before_verify', "require(all(not Q.same_process(i, d.identity(i['pid'])) for i in members(self.saved)), 'actual_cold_start_writer_not_drained')", "pass", SessionTests, 'test_remaining_writer_refuses_verify'),
        ('output_privacy', "match and match[1] in FIELDS", "match", SchemaTests, 'test_output_drops_untrusted_secret_fields'),
        ('inventory_bound', "require(len(rows) <= 128, 'bootstrap_inventory_overflow')", "pass", BootstrapTests, 'test_inventory_uid_filter_and_cap'),
        ('unknown_payload_refusal', "require(set(rows) <= covered, 'bootstrap_unknown_uid_payload_refused')", "pass", BootstrapTests, 'test_unknown_payload_prevents_any_stop_install_signal'),
        ('restore_after_cleanup_refusal', "restore_error = error\n                evidence.append('FAIL fixture_cleanup_' + safe_error(error))", "raise", RestoreTests, 'test_cleanup_refusal_does_not_skip_person_restore'),
    ]
    for name, before, after, test_class, test_name in mutations:
        if before not in source:
            raise AssertionError('mutation anchor missing: ' + name)
        candidate = types.ModuleType('bb9_removed_guard'); candidate.__file__ = str(Path(__file__).with_name('bb9_component_update_acceptance.py'))
        exec(compile(source.replace(before, after), candidate.__file__, 'exec'), candidate.__dict__)
        H = candidate
        result = unittest.TextTestRunner(stream=io.StringIO()).run(unittest.TestSuite([test_class(test_name)]))
        if result.wasSuccessful():
            raise AssertionError('removed guard unexpectedly green: ' + name)
        print('PASS removed_fix_red ' + name)
    H = restored
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__]))
    return 0 if result.wasSuccessful() else 1

if __name__=='__main__':
    if sys.argv[1:] == ['--red-proof']:
        raise SystemExit(red_proof())
    unittest.main(verbosity=2)
