import contextlib
import io
import json
from pathlib import Path
import tempfile
import types
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET
import run

REMOVE_BODY = ('This removes the installed agent from this phone. Your accounts '
               'and conversations stay, and you can install it again.')

def node(text, description=''):
    return ET.Element('node', {'text':text, 'content-desc':description})

class Tests(unittest.TestCase):
    def test_remove_uses_ba10_public_confirmation(self):
        # Current localized BA10 copy supersedes the preliminary contract.
        taps=[]
        d=types.SimpleNamespace(launch_agents=lambda: None,
            text=lambda node: node, tap_node=lambda node: taps.append(node))
        ports=run.Ports(d,None,None,{'fx':{'name':'fx'}},'fx')
        pages=iter([['Remove fx'], ['Remove fx?', REMOVE_BODY,
            'Cancel', 'Remove fx']])
        ports.ui=lambda: next(pages)
        self.assertTrue(ports.app_remove('fx','fx'))
        self.assertEqual(taps,['Remove fx','Remove fx'])

    def test_remove_rejects_ambiguous_confirmation(self):
        taps=[]
        d=types.SimpleNamespace(launch_agents=lambda: None,
            text=lambda node: node, tap_node=lambda node: taps.append(node))
        ports=run.Ports(d,None,None,{'fx':{'name':'fx'}},'fx')
        pages=iter([['Remove fx'], ['Remove fx?', REMOVE_BODY,
            'Remove fx', 'Remove fx']])
        ports.ui=lambda: next(pages)
        self.assertFalse(ports.app_remove('fx','fx'))
        self.assertEqual(taps,['Remove fx'])

    def current_ports(self, pages):
        taps=[]
        d=types.SimpleNamespace(launch_agents=lambda: None,
            text=lambda n: (n.get('text') or n.get('content-desc') or ''),
            tap_node=lambda n: taps.append((n.get('text'),n.get('content-desc'))))
        ports=run.Ports(d,None,None,{'fx':{'name':'fx'}},'fx')
        frames=iter(pages)
        ports.ui=lambda: next(frames)
        return ports,taps

    def test_current_remove_opens_only_target_sheet_then_confirms_and_dismisses(self):
        ports,taps=self.current_ports([
            [node('Claude Code\nReady'),node('Sign in','Sign in to Claude Code'),
             node('fx\nSign in needed'),node('Sign in','Sign in to fx')],
            [node('Sign in with fx'),node('Remove fx')],
            [node('Remove fx?'),node(REMOVE_BODY),node('Cancel'),node('Remove fx')],
            [node('fx removed. Freed 12 MB.'),node('Done')],
            [node('fx\nNot installed · 12 MB'),node('Install','Install fx')],
        ])
        self.assertTrue(ports.app_remove('fx','fx'))
        self.assertTrue(ports.target_not_installed_visible('fx'))
        self.assertEqual(taps,[('Sign in','Sign in to fx'),('Remove fx',''),
                              ('Remove fx',''),('Done','')])

    def test_target_scoped_semantics_survive_generic_visible_chip_copy(self):
        ports,_=self.current_ports([])
        self.assertEqual(ports.text(node('Sign in','Sign in to fx')),'Sign in to fx')
        self.assertEqual(ports.text(node('Install','Install fx')),'Install fx')

    def test_install_opens_scoped_sheet_then_uses_its_unique_install_action(self):
        ports,taps=self.current_ports([
            [node('Install','Install Claude Code'),node('Install','Install fx')],
            [node('Install fx')],
        ])
        ports.tap_install('fx')
        self.assertEqual(taps,[('Install','Install fx'),('Install fx','')])

    def test_install_refuses_unscoped_or_foreign_action(self):
        ports,taps=self.current_ports([[node('Install'),node('Install','Install Claude Code')]])
        with self.assertRaisesRegex(RuntimeError,'target_install_action_missing'):
            ports.tap_install('fx')
        self.assertEqual(taps,[])

    def test_remove_does_not_use_unscoped_sign_in_or_another_agent(self):
        ports,taps=self.current_ports([[node('fx\nSign in needed'),node('Sign in'),
                                      node('Sign in','Sign in to Claude Code')]])
        self.assertFalse(ports.app_remove('fx','fx'))
        self.assertEqual(taps,[])

    def test_remove_rejects_foreign_confirmation_button(self):
        ports,taps=self.current_ports([
            [node('Remove fx')],
            [node('Remove fx?'),node(REMOVE_BODY),node('Remove Claude Code')],
        ])
        self.assertFalse(ports.app_remove('fx','fx'))
        self.assertEqual(taps,[('Remove fx','')])

    def test_not_installed_requires_row_after_done_not_just_success_copy(self):
        ports,taps=self.current_ports([
            [node('fx removed. Freed 12 MB.'),node('Done')],
            [node('Claude Code\nReady')],
        ])
        self.assertFalse(ports.target_not_installed_visible('fx'))
        self.assertEqual(taps,[('Done','')])

    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.path=Path(self.tmp.name)/'receipt.json'
        self.path.write_text(json.dumps({'normal':dict(apk='/does-not-exist/normal.apk',build=2197,sha256='a'*64,sourceRevision='b'*40,dartDefines={})}))
    def test_plan_never_loads_device_helpers_or_verifies_apk(self):
        argv=['run.py','fx','--case','launch','--manifest',str(self.path)]
        with patch('sys.argv',argv),patch.object(run,'legacy_ports',side_effect=AssertionError('device code loaded')),patch.object(run,'verify_artifact',side_effect=AssertionError('APK tool called')),contextlib.redirect_stdout(io.StringIO()) as out:
            run.main()
        self.assertFalse(json.loads(out.getvalue())['deviceTouched'])
    def test_missing_artifact_execute_stops_before_device_or_lock(self):
        argv=['run.py','fx','--case','uninstall','--manifest',str(self.path),'--execute']
        with patch('sys.argv',argv),patch.object(run,'legacy_ports',side_effect=AssertionError('device code loaded')):
            with self.assertRaisesRegex(RuntimeError,'artifact_unavailable'): run.main()
    def test_sha_mismatch_stops_before_signer_tool(self):
        apk=Path(self.tmp.name)/'candidate.apk';apk.write_bytes(b'not-an-apk')
        with patch.object(run.subprocess,'run',side_effect=AssertionError('signer invoked')):
            with self.assertRaisesRegex(RuntimeError,'artifact_hash_mismatch'):
                run.verify_artifact(dict(apk=str(apk),sha256='a'*64))

    def session(self, *, another=False, restore_fails=False):
        device=types.SimpleNamespace(
            configure=lambda output: None, end_session=lambda: None,
            shot=lambda name: None,
            adb=lambda *args, **kwargs: 'device' if args==('get-state',) else 'x86_64')
        probe=types.SimpleNamespace(require_idle_setup=lambda: None)
        ports=types.SimpleNamespace(
            available_storage_bytes=lambda: 1800000000,
            target_inventory=lambda target: {'leftovers':another and target=='codex','targetPids':[]},
            install_if_absent=lambda: None)
        argv=['run.py','fx','--case','uninstall','--manifest',str(self.path),
              '--execute','--output',self.tmp.name]
        lock=Path(self.tmp.name)/'emulator.lock'
        selected=[]
        def select(*args):
            selected.append(args)
            if restore_fails and len(selected)==2: raise RuntimeError('private restore detail')
        return device,probe,ports,argv,lock,selected,select

    def test_failed_restore_is_fatal_after_evidence_and_unlock(self):
        d,p,ports,argv,lock,selected,select=self.session(restore_fails=True)
        with patch('sys.argv',argv),patch.object(run,'LOCK',lock),\
             patch.object(run,'verify_artifact'),\
             patch.object(run,'legacy_ports',return_value=(d,p,None,{'fx':{'name':'fx'}})),\
             patch.object(run,'Ports',return_value=ports),\
             patch.object(run,'select_apk',side_effect=select),\
             patch.object(run,'run_uninstall',return_value={'state':'partial'}):
            with self.assertRaisesRegex(RuntimeError,'normal_app_restoration_failed'):
                run.main()
        result=json.loads((Path(self.tmp.name)/'fx-uninstall-observations.json').read_text())
        self.assertTrue(result['restorationBlocked'])
        self.assertFalse(result['normalRestored'])
        self.assertNotIn('private',repr(result))
        with lock.open('a') as handle:
            run.fcntl.flock(handle,run.fcntl.LOCK_EX|run.fcntl.LOCK_NB)

    def test_rejected_preflight_does_not_select_or_restore_app(self):
        d,p,ports,argv,lock,selected,select=self.session(another=True)
        with patch('sys.argv',argv),patch.object(run,'LOCK',lock),\
             patch.object(run,'verify_artifact'),\
             patch.object(run,'legacy_ports',return_value=(d,p,None,{'fx':{'name':'fx'}})),\
             patch.object(run,'Ports',return_value=ports),\
             patch.object(run,'select_apk',side_effect=select):
            with self.assertRaisesRegex(RuntimeError,'another_target_installed'):
                run.main()
        self.assertEqual(selected,[])

    def test_select_reverifies_even_when_installed_hash_matches(self):
        artifact={'apk':'/unused.apk','sha256':'a'*64,'build':2197}
        d=types.SimpleNamespace(PKG='example',adb=lambda *args,**kwargs:
            'package:/installed.apk' if 'path' in args else
            'versionCode=2197 ' if 'dumpsys' in args else 'a'*64+' /installed.apk')
        ports=types.SimpleNamespace(d=d,p=types.SimpleNamespace(require_idle_setup=lambda: None))
        with patch.object(run,'verify_artifact') as verified:
            run.select_apk(ports,artifact)
        verified.assert_called_once_with(artifact)

    def test_artifact_change_during_signer_check_is_rejected(self):
        apk=Path(self.tmp.name)/'candidate.apk'; apk.write_bytes(b'candidate')
        artifact={'apk':str(apk),'sha256':run.hashlib.sha256(b'candidate').hexdigest()}
        def signer(*args,**kwargs):
            apk.write_bytes(b'replaced')
            return types.SimpleNamespace(returncode=0,stdout=run.SIGNER.encode())
        with patch.object(run.shutil,'which',return_value='/fake/apksigner'),\
             patch.object(run.subprocess,'run',side_effect=signer):
            with self.assertRaisesRegex(RuntimeError,'artifact_changed'):
                run.verify_artifact(artifact)

    def test_port_ui_uses_single_bounded_dump_instead_of_legacy_retries(self):
        calls=[]
        def adb(*args,**kwargs):
            calls.append((args,kwargs))
            return '<hierarchy><node text="Agents"/></hierarchy>' if 'cat' in args else ''
        d=types.SimpleNamespace(adb=adb,ui=lambda: self.fail('legacy retry loop used'))
        ports=run.Ports(d,None,None,{'fx':{'name':'fx'}},'fx')
        self.assertEqual(len(ports.ui()),1)
        self.assertEqual(len(calls),2)
        self.assertEqual([kwargs.get('timeout') for _,kwargs in calls],[12,12])

    def test_snapshot_projects_dependency_download_counters_without_raw_errors(self):
        job={'jobId':'fresh-job','state':'failed','params':{'agentInstallGuard':{
            'agentId':'fx','minimumFreeBytes':str(8589934592)}},'components':{
            'node':{'state':'done','done':123,'error':'private data'},
            'agent-fx':{'state':'failed','done':0,'error':run.STORAGE_COPY},
            'bad\nprivate':{'state':'done','done':999}}}
        d=types.SimpleNamespace(adb=lambda *args,**kwargs: json.dumps(job))
        p=types.SimpleNamespace(FILES='/private/files')
        ports=run.Ports(d,p,None,{'fx':{'name':'fx'}},'fx')
        snapshot=ports.setup_snapshot()
        self.assertEqual(set(snapshot['components']),{'node','agent-fx'})
        self.assertEqual(snapshot['components']['node']['done'],123)
        self.assertIsNone(snapshot['components']['node']['declaredMinimumFreeBytes'])
        self.assertEqual(snapshot['components']['agent-fx']['errorCode'],'low_storage')
        self.assertEqual(snapshot['components']['agent-fx']['declaredMinimumFreeBytes'],8589934592)
        self.assertNotIn('private',repr(snapshot))
if __name__=='__main__': unittest.main()
