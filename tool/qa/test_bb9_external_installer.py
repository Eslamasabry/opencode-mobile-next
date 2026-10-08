"""Mock-only external installer checks. Every Popen and kernel operation is fake."""
import copy
import io
import sys
import types
from pathlib import Path
import unittest
from unittest import mock

try:
    import bb9_external_installer as E
except ModuleNotFoundError:
    from tool.qa import bb9_external_installer as E

UID=10234
BOOT='12345678-1234-1234-1234-123456789abc'
TOKEN='a'*64
NATIVE='/data/app/~~fixture/'+E.PACKAGE+'-fixture/lib/x86_64'

def process(pid,parent=1,group=None,session=None):
    return dict(pid=pid,startTicks=100+pid,parent=parent,group=group or pid,session=pid if session is None else session,state='S')

def exported(wrapper=False):
    command=[NATIVE+'/libproot.so','--root-id','--kill-on-exit','--rootfs='+E.PRIVATE+'/files/linux/ubuntu',
             '/usr/bin/env','OC_RUNTIME_OWNER='+TOKEN,'/usr/bin/setsid','/bin/sh','-c',
             "printf 'OC-INSTALL-1 %s %s\\n' '"+TOKEN+"' \"$$\"\nIFS= read -r permit || exit 78\n[ \"$permit\" = '"+TOKEN+"' ] || exit 78\nexec /bin/sleep 180"]
    if wrapper:command=[NATIVE+'/libaiteam_sandbox.so','--read-only',NATIVE,'--',*command]
    return dict(version=1,uid=UID,app=E.wire_identity(process(42,session=0)),ticketId=TOKEN,command=command,
                environment=dict(PROOT_LOADER=NATIVE+'/libproot-loader.so',PROOT_TMP_DIR=E.PRIVATE+'/cache/proot-tmp',
                                 LD_LIBRARY_PATH=NATIVE,OC_RUNTIME_OWNER=TOKEN))

class Pipe:
    def __init__(self,fd):self.fd=fd;self.closed=False
    def fileno(self):return self.fd
    def close(self):self.closed=True

class Channel:
    def __init__(self):
        self.stdin=Pipe(10);self.stdout=Pipe(11);self.stderr=Pipe(12)
        self.wait=mock.Mock(return_value=0);self.terminate=mock.Mock();self.kill=mock.Mock()

class KernelDevice:
    serial='emulator-5554'
    def adb(self,*a,**k):raise AssertionError('No real device invocation permitted')

class Fixture(E.ExternalInstaller):
    def __init__(self):
        super().__init__(KernelDevice())
        self.identities={42:process(42,session=0),80:process(80,parent=60),81:process(81,parent=80),82:process(82,parent=81,group=81,session=81)}
        self.uids={};self.nonces={};self.argv=None;self.durable=None;self.signals=[];self.reads=[];self.boot=BOOT
    def _identity(self,pid):return copy.deepcopy(self.identities.get(pid))
    def _uid(self,pid):return self.uids.get(pid,[UID]*4)
    def _read(self,pid,name,limit=4096):
        if name=='cmdline':return '\x00'.join(self.argv or self.export['command'])+'\x00'
        if name=='environ':return self.nonces.get(pid,'OC_RUNTIME_OWNER='+TOKEN+'\x00')
        raise AssertionError(name)
    def _inventory(self):return {pid:item['parent'] for pid,item in self.identities.items()}
    def _shell(self,script,limit=E.PRIVATE_BYTES):
        self.reads.append(script)
        if script=='cat /proc/sys/kernel/random/boot_id':return self.boot+'\n'
        raise AssertionError('Unexpected mock shell operation')
    def _durable(self):return copy.deepcopy(self.durable)
    def _signal(self,item,signal):
        self.signals.append((item['pid'],item['startTicks'],signal));self.identities.pop(item['pid'],None)
    def ticket(self):
        return dict(version=1,id=TOKEN,rootfsGeneration='b'*64,targets=['OPENCODE2'],operation='INSTALL',
                    ownership=dict(version=1,boot=BOOT,nonce=TOKEN,generation=1,root=E.wire_identity(self.root),
                                   leader=E.wire_identity(self.leader),other=[self.export['app']]),
                    observed=[E.wire_identity(i) for i in self.witnessed.values()])

class ExternalTests(unittest.TestCase):
    def setUp(self):
        self.channel=Channel();self.output=[b'OC-BB9-ROOT-1 80\n',('OC-INSTALL-1 '+TOKEN+' 81\n').encode()];self.writes=[]
        self.ready=lambda reads,writes,errors,timeout:(reads[:1],writes,[])
        patches=[mock.patch.object(E.subprocess,'Popen',return_value=self.channel),mock.patch.object(E.os,'set_blocking'),
                 mock.patch.object(E.select,'select',side_effect=lambda *a:self.ready(*a)),
                 mock.patch.object(E.os,'read',side_effect=self.read),mock.patch.object(E.os,'write',side_effect=self.write),
                 mock.patch.object(E.time,'sleep')]
        self.patches=patches;self.popen=patches[0].start();self.addCleanup(patches[0].stop)
        for p in patches[1:]:p.start();self.addCleanup(p.stop)
    def read(self,fd,size):return self.output.pop(0) if self.output else b''
    def write(self,fd,data):self.writes.append(data);return len(data)
    def started(self,value=None):
        helper=Fixture();result=helper.start(exported() if value is None else value)
        self.assertEqual(result['root']['pid'],80);self.assertEqual(result['leader']['pid'],81)
        return helper
    def committed(self):
        helper=self.started();ticket=helper.ticket();helper.durable=ticket;helper.mark_committed(ticket);return helper
    def test_persistent_raw_channel_and_protected_command_preserved(self):
        value=exported(wrapper=True);helper=self.started(value)
        argv=self.popen.call_args.args[0]
        self.assertEqual(argv[:5],['adb','-s','emulator-5554','shell','-T'])
        self.assertIn('exec /system/bin/run-as '+E.PACKAGE+' --user 0 /system/bin/env -i',argv[-1])
        self.assertIn(NATIVE+'/libaiteam_sandbox.so',argv[-1])
        self.assertEqual(helper.export['command'],value['command']);self.assertFalse(self.channel.stdin.closed)
        self.assertEqual(self.writes,[])
    def test_protected_wrapper_exec_into_exact_proot_is_supported(self):
        helper=Fixture();value=exported(wrapper=True);helper.argv=value['command'][4:]
        helper.start(value);self.assertTrue(helper.root_proven)
    def test_equals_in_apk_path_cannot_be_consumed_as_env_assignment(self):
        value=exported()
        old=NATIVE;new=NATIVE.replace('~~fixture','~~fixture==')
        value['command']=[part.replace(old,new) for part in value['command']]
        value['environment']={key:val.replace(old,new) for key,val in value['environment'].items()}
        self.started(value)
        words=E.shlex.split(self.popen.call_args.args[0][-1].split('; exec ',1)[1])
        args=words[words.index('-i')+1:]
        while args and '=' in args[0]:args.pop(0)
        self.assertEqual(args[:4],['/system/bin/sh','-c','exec "$@"','oc-bb9-owned-launch'])
        self.assertEqual(args[4:],value['command'])
    def test_start_never_permits_without_durable_commit(self):
        helper=self.started()
        with self.assertRaisesRegex(E.Refused,'external_permit_requires_commit'):helper.permit()
        self.assertEqual(self.writes,[])
    def test_matching_actual_durable_ticket_permits_once(self):
        helper=self.committed();helper.permit()
        self.assertEqual(self.writes,[(TOKEN+'\n').encode()]);self.assertTrue(helper.permitted)
        with self.assertRaises(E.Refused):helper.permit()
    def test_durable_replaced_after_mark_refuses_permit(self):
        helper=self.committed();helper.durable['id']='c'*64
        with self.assertRaisesRegex(E.Refused,'external_durable_ticket_mismatch'):helper.permit()
        self.assertEqual(self.writes,[])
    def test_prepared_mismatched_and_wrong_boot_tickets_refused(self):
        for change in [lambda t:t['ownership'].update(root=None,leader=None),lambda t:t.update(id='c'*64),
                       lambda t:t['ownership']['root'].update(startTicks=999),lambda t:t['ownership'].update(nonce='c'*64),
                       lambda t:t['ownership'].update(boot='99999999-1234-1234-1234-123456789abc')]:
            self.output=[b'OC-BB9-ROOT-1 80\n',('OC-INSTALL-1 '+TOKEN+' 81\n').encode()]
            helper=self.started();ticket=helper.ticket();change(ticket);helper.durable=ticket
            with self.assertRaises(E.Refused):helper.mark_committed(ticket)
            self.assertEqual(self.writes,[])
    def test_missing_persisted_ticket_refuses_mark(self):
        helper=self.started()
        with self.assertRaisesRegex(E.Refused,'external_durable_ticket_mismatch'):helper.mark_committed(helper.ticket())
    def test_export_schema_env_size_and_private_paths_fail_before_spawn(self):
        mutations=[lambda v:v.update(version=True),lambda v:v.update(uid=1),lambda v:v.update(extra='private'),
                   lambda v:v['environment'].update(extra='private'),lambda v:v['environment'].update(OC_RUNTIME_OWNER='c'*64),
                   lambda v:v['command'].__setitem__(0,'/system/bin/sh'),
                   lambda v:v['command'].__setitem__(3,'--rootfs=/data/user/0/other/files/linux/ubuntu'),
                   lambda v:v['command'].append('x'*32769),lambda v:v['command'].append('bad\x00value')]
        for change in mutations:
            value=exported();change(value)
            with self.subTest(change=change),self.assertRaises(E.Refused):Fixture().start(value)
        self.popen.assert_not_called()
    def test_other_device_refused(self):
        d=KernelDevice();d.serial='other'
        with self.assertRaises(E.Refused):E.ExternalInstaller(d)
    def test_root_and_leader_wrong_uid_refuse(self):
        for pid in [80,81]:
            self.output=[b'OC-BB9-ROOT-1 80\n',('OC-INSTALL-1 '+TOKEN+' 81\n').encode()]
            helper=Fixture();helper.uids[pid]=[UID+1]*4
            with self.assertRaises(E.Refused):helper.start(exported())
            self.assertEqual(self.writes,[])
    def test_root_owner_uid_proof_is_required(self):
        helper=Fixture();helper.export=exported();helper.root=helper._identity(80);helper.uids[80]=[UID+1]*4
        with self.assertRaisesRegex(E.Refused,'external_root_owner_invalid'):helper._prove_root()
    def test_wrong_sessions_refuse(self):
        for pid in [80,81]:
            self.output=[b'OC-BB9-ROOT-1 80\n',('OC-INSTALL-1 '+TOKEN+' 81\n').encode()]
            helper=Fixture();helper.identities.pop(82);helper.identities[pid]['session']=0
            with self.assertRaises(E.Refused):helper.start(exported())
    def test_gate_nonce_and_root_header_refuse(self):
        for output in [[b'OC-BB9-ROOT-1 42\n'],[b'OC-BB9-ROOT-1 80\n',('OC-INSTALL-1 '+'c'*64+' 81\n').encode()],
                       [b'private raw error\n']]:
            self.output=output
            with self.assertRaises(E.Refused):Fixture().start(exported())
            self.assertEqual(self.writes,[])
    def test_root_argv_nonce_and_leader_ancestry_refuse(self):
        for change in [lambda h:setattr(h,'argv',['unexpected']),lambda h:h.nonces.update({80:'OTHER=private\x00'}),
                       lambda h:h.identities[81].update(parent=1)]:
            self.output=[b'OC-BB9-ROOT-1 80\n',('OC-INSTALL-1 '+TOKEN+' 81\n').encode()]
            helper=Fixture();change(helper)
            with self.assertRaises(E.Refused):helper.start(exported())
    def test_pid_reuse_at_permit_refuses_without_write(self):
        helper=self.committed();helper.identities[80]['startTicks']+=1
        with self.assertRaises(E.Refused):helper.permit()
        self.assertEqual(self.writes,[])
    def test_app_dies_before_permit_refuses_without_write(self):
        helper=self.committed();helper.identities.pop(42)
        with self.assertRaisesRegex(E.Refused,'external_app_changed'):helper.permit()
        self.assertEqual(self.writes,[])
    def test_header_stdout_stderr_caps_and_eof(self):
        for outputs in [[b'x'*257],[b'x'*129+b'\n'],[b'']]:
            self.output=outputs
            with self.assertRaises(E.Refused):Fixture().start(exported())
        self.output=[b'x'*4097];self.ready=lambda r,w,e,t:([12] if 12 in r else [11],w,[])
        with self.assertRaisesRegex(E.Refused,'external_stderr_overflow'):Fixture().start(exported())
    def test_closed_stderr_is_removed_without_misclassifying_stdout(self):
        self.output=[b'',b'OC-BB9-ROOT-1 80\n',('OC-INSTALL-1 '+TOKEN+' 81\n').encode()]
        first=[True];reads=[]
        def ready(r,w,e,t):
            reads.append(r)
            if first[0]:first[0]=False;return [12],w,[]
            return [11],w,[]
        self.ready=ready;self.started();self.assertEqual(reads[1:],[ [11],[11]])
    def test_cleanup_before_permit_closes_stdin_and_signals_exact_children_root_last(self):
        helper=self.started();helper.drain()
        self.assertTrue(self.channel.stdin.closed);self.assertEqual(self.writes,[])
        self.assertEqual([s[0] for s in helper.signals],[81,82,80]);self.assertTrue(helper.drained)
        self.assertEqual(set(helper.identities),{42})
    def test_cleanup_after_app_death_keeps_independent_root_until_drain(self):
        helper=self.committed();helper.permit();helper.identities.pop(42)
        self.assertIn(80,helper.identities);helper.drain();self.assertFalse(helper.identities)
    def test_cleanup_refuses_reused_pid_without_signaling(self):
        helper=self.started();helper.identities[80]['startTicks']+=1
        with self.assertRaisesRegex(E.Refused,'external_pid_reused'):helper.drain()
        self.assertEqual(helper.signals,[]);self.assertFalse(helper.drained)
    def test_unwitnessed_reparented_member_is_not_signaled(self):
        helper=self.started();helper.identities[83]=process(83,parent=1,group=81,session=81)
        with self.assertRaisesRegex(E.Refused,'external_child_unproven'):helper.drain()
        self.assertEqual(helper.signals,[])
    def test_failed_launch_without_header_cannot_claim_tree_drained(self):
        self.output=[b''];helper=Fixture()
        with self.assertRaises(E.Refused):helper.start(exported())
        with self.assertRaisesRegex(E.Refused,'external_cleanup_unproven'):helper.drain()
        self.assertFalse(helper.drained);self.assertEqual(helper.signals,[])
    def test_partial_permit_write_retries_without_duplicate_bytes(self):
        helper=self.committed();seen=[]
        def partial(fd,data):seen.append(data[:7]);return min(7,len(data))
        with mock.patch.object(E.os,'write',side_effect=partial):helper.permit()
        self.assertEqual(b''.join(seen),(TOKEN+'\n').encode())
    def test_launcher_failure_projection_is_bounded_and_contains_no_raw_data(self):
        helper=self.started();self.channel.poll=mock.Mock(return_value=1)
        self.output=[b'policy_path_unavailable secret-placeholder-NEVER-OUTPUT',b'']
        with mock.patch.object(E.select,'select',return_value=([12],[],[])):
            values=helper.failure_diagnostics()
        self.assertTrue(values['policy_path_unavailable'])
        self.assertTrue(all(type(v) is bool for v in values.values()))
        self.assertNotIn('secret-placeholder',str(values))
    def test_root_exit_before_gate_has_a_distinct_refusal(self):
        helper=Fixture();helper.identities.pop(80)
        with self.assertRaisesRegex(E.Refused,'external_root_exited_before_gate'):helper.start(exported())
        self.assertEqual(self.writes,[])
    def test_no_raw_launcher_data_printed(self):
        with mock.patch('builtins.print') as printed:
            helper=self.committed();helper.permit();helper.drain()
        printed.assert_not_called()

class AdditionalTests(unittest.TestCase):
    setUp=ExternalTests.setUp
    read=ExternalTests.read
    write=ExternalTests.write
    started=ExternalTests.started
    committed=ExternalTests.committed
    def test_boot_changes_after_mark_refuses_permit(self):
        helper=self.committed();helper.boot='99999999-1234-1234-1234-123456789abc'
        with self.assertRaisesRegex(E.Refused,'external_boot_changed'):helper.permit()
        self.assertEqual(self.writes,[])
    def test_app_uid_changes_before_permit_refuses(self):
        helper=self.committed();helper.uids[42]=[UID+1]*4
        with self.assertRaisesRegex(E.Refused,'external_app_changed'):helper.permit()
        self.assertEqual(self.writes,[])
    def test_launch_header_wait_is_bounded_to_twenty_seconds(self):
        helper=Fixture();elapsed=[0]
        def now():
            value=elapsed[0];elapsed[0]+=2;return value
        self.ready=lambda r,w,e,t:([],[],[])
        with mock.patch.object(E.time,'monotonic',side_effect=now),self.assertRaisesRegex(E.Refused,'external_deadline_exceeded'):
            helper.start(exported())
        self.assertLessEqual(elapsed[0],22)
        self.assertEqual(self.writes,[])
    def test_only_captured_host_channel_is_terminated_if_reaping_times_out(self):
        import subprocess
        helper=self.started()
        self.channel.wait.side_effect=[subprocess.TimeoutExpired(['owned adb'],0),subprocess.TimeoutExpired(['owned adb'],0),0]
        helper.drain()
        self.channel.terminate.assert_called_once();self.channel.kill.assert_called_once()
    def test_qualified_zombie_root_does_not_create_an_unknown_writer(self):
        helper=self.started();helper.identities[80]['state']='Z';helper.identities.pop(81);helper.identities.pop(82)
        helper.drain();self.assertTrue(helper.drained);self.assertEqual(helper.signals,[])
    def test_unproven_root_exit_is_not_a_tree_drain_claim(self):
        helper=Fixture();self.output=[b'OC-BB9-ROOT-1 80\n',b'wrong header\n']
        with self.assertRaises(E.Refused):helper.start(exported())
        helper.identities.pop(80)
        with self.assertRaisesRegex(E.Refused,'external_cleanup_unproven'):helper.drain()
        self.assertFalse(helper.drained)
    def test_command_private_path_traversal_refuses(self):
        value=exported();bad=NATIVE.replace('/~~fixture/','/../')
        value['command']=[v.replace(NATIVE,bad) for v in value['command']]
        value['environment']={k:v.replace(NATIVE,bad) for k,v in value['environment'].items()}
        with self.assertRaisesRegex(E.Refused,'external_private_command_invalid'):Fixture().start(value)
        self.popen.assert_not_called()


def red_proof():
    global E
    restored=E;source=Path(__file__).with_name('bb9_external_installer.py').read_text()
    mutations=[
      ('permit_commit', "require(self.committed is not None and not self.permitted, 'external_permit_requires_commit')", 'pass', ExternalTests,'test_start_never_permits_without_durable_commit'),
      ('durable_permit', "require(self._durable() == self.committed, 'external_durable_ticket_mismatch')", 'pass', ExternalTests,'test_durable_replaced_after_mark_refuses_permit'),
      ('root_uid', "self._uid(pid) == [self.export['uid']] * 4 and self._nonce(pid)", 'self._nonce(pid)', ExternalTests,'test_root_owner_uid_proof_is_required'),
      ('leader_session', "self.leader['pid'] == self.leader['group'] == self.leader['session']", 'True', ExternalTests,'test_wrong_sessions_refuse'),
      ('gate_nonce', "gate and gate[1] == self.export['ticketId']", 'gate', ExternalTests,'test_gate_nonce_and_root_header_refuse'),
      ('private_command', "argv == original or (original[0].endswith('/libaiteam_sandbox.so') and argv == original[proot:])", 'True', ExternalTests,'test_root_argv_nonce_and_leader_ancestry_refuse'),
      ('stderr_cap', "self.stderr_count <= STDERR_BYTES", 'True', ExternalTests,'test_header_stdout_stderr_caps_and_eof'),
      ('pid_reuse', "current is None or old['startTicks'] == current['startTicks']", 'True', ExternalTests,'test_cleanup_refuses_reused_pid_without_signaling'),
      ('unknown_child', "current is None or current['state'] in {'Z','X'} or current['session'] not in sessions", 'True', ExternalTests,'test_unwitnessed_reparented_member_is_not_signaled'),
      ('cleanup_proof', "require(self.root_proven, 'external_cleanup_unproven')", 'pass', ExternalTests,'test_failed_launch_without_header_cannot_claim_tree_drained'),
      ('boot_permit', "require(self._shell('cat /proc/sys/kernel/random/boot_id',128).strip() == self.committed['ownership']['boot'], 'external_boot_changed')", 'pass', AdditionalTests,'test_boot_changes_after_mark_refuses_permit'),
    ]
    for name,before,after,cls,test in mutations:
        assert before in source, name
        module=types.ModuleType('external_removed_guard');module.__file__=str(Path(__file__).with_name('bb9_external_installer.py'))
        exec(compile(source.replace(before,after),module.__file__,'exec'),module.__dict__);E=module
        # Fixture must follow the mutated implementation, retaining only fake
        # kernel adapters; inherited production methods bind to the new class.
        original_bases=Fixture.__bases__;Fixture.__bases__=(module.ExternalInstaller,)
        try:
            result=unittest.TextTestRunner(stream=io.StringIO()).run(unittest.TestSuite([cls(test)]))
            assert not result.wasSuccessful(), 'removed guard unexpectedly green: '+name
        finally:Fixture.__bases__=original_bases
        print('PASS removed_fix_red '+name)
    E=restored
    suite=unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__])
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    return 0 if result.wasSuccessful() else 1

if __name__=='__main__':
    if sys.argv[1:]==['--red-proof']:raise SystemExit(red_proof())
    unittest.main(verbosity=2)
