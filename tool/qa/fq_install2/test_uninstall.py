import unittest
from uninstall import run_uninstall

ABSENT = dict(allocatedBytes=0, targetPids=[], leftovers=False, staging=[], lockPresent=False)
PRESENT = dict(allocatedBytes=4096, targetPids=[], pinMatches=True, linkMatches=True)
class Device:
    def __init__(self): self.inventories=[dict(PRESENT), dict(ABSENT)]; self.calls=[]; self.available=True; self.visible=True; self.t=0
    def available_storage_bytes(self): self.calls.append('storage'); return 1000000000
    def target_inventory(self, _): self.calls.append('inventory'); return self.inventories.pop(0) if len(self.inventories)>1 else self.inventories[0]
    def app_remove(self, agent, name): self.calls.append('remove.'+agent); return self.available
    def target_not_installed_visible(self, _): return self.visible
    def clock(self): return self.t
    def sleep(self, n): self.t+=n
class Tests(unittest.TestCase):
    def run_case(self,d): return run_uninstall('fx','fx',d,clock=d.clock,sleep=d.sleep,timeout_seconds=1)
    def test_real_app_removal_and_storage_first(self):
        d=Device(); r=self.run_case(d); self.assertEqual(r['state'],'pass'); self.assertEqual(d.calls[0],'storage'); self.assertTrue(r['facts']['removedViaApp']); self.assertEqual(r['facts']['bytesFreed'],4096)
    def test_missing_action_never_falls_back_to_shell_cleanup(self):
        d=Device();d.available=False;self.assertEqual(self.run_case(d)['code'],'app_removal_action_missing');self.assertEqual(d.calls,['storage','inventory','remove.fx'])
    def test_live_target_is_refused_before_app_action(self):
        d=Device(); d.inventories[0]['targetPids']=[123]
        with self.assertRaises(RuntimeError): self.run_case(d)
        self.assertNotIn('remove.fx',d.calls)
    def test_claude_is_refused_before_any_port_call(self):
        d=Device()
        with self.assertRaises(ValueError): run_uninstall('claude','Claude Code',d)
        self.assertEqual(d.calls,[])
    def test_absent_payload_waits_for_completion_sheet_and_fresh_row(self):
        d=Device(); frames=iter([False,False,True])
        d.target_not_installed_visible=lambda _: next(frames)
        result=self.run_case(d)
        self.assertEqual(result['state'],'pass')
        self.assertEqual(d.t,.5)

    def test_stale_ready_row_is_partial(self):
        d=Device();d.visible=False;self.assertEqual(self.run_case(d)['state'],'partial')
    def test_orphan_process_prevents_pass(self):
        d=Device(); d.inventories[1]['targetPids']=[123]; self.assertEqual(self.run_case(d)['code'],'app_removal_timeout')
    def test_stage_lock_and_link_prevent_pass(self):
        for key,value in [('staging',['stage']),('lockPresent',True),('leftovers',True),('allocatedBytes',1024)]:
            d=Device();d.inventories[1][key]=value;self.assertEqual(self.run_case(d)['state'],'fail')
if __name__=='__main__': unittest.main()
