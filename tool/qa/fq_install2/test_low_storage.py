"""Offline low-storage driver checks. No emulator, Flutter or account access."""

import unittest

from low_storage import LowStorageProofError, QA_MIN_FREE_BYTES, run_low_storage


def inventory():
    return {'leftovers': False, 'targetPids': [], 'allocatedBytes': 0,
            'staging': [], 'lockPresent': False}


def job(identity='new', state='failed', component_state='failed', **fields):
    return {'jobId': identity, 'state': state, 'components': {
        'agent-fx': {'state': component_state, 'done': None,
                     'declaredMinimumFreeBytes': QA_MIN_FREE_BYTES,
                     'errorCode': 'low_storage', **fields}}}


class FakePorts:
    def __init__(self):
        self.available = 1700000000
        self.inventory = inventory()
        self.frames = [job('old', 'done', 'done'), job()]
        self.calls = []
        self.guidance = True
        self.time = 0

    def available_storage_bytes(self):
        self.calls.append('storage')
        return self.available

    def target_inventory(self, agent_id):
        self.calls.append('inventory')
        return self.inventory

    def setup_snapshot(self):
        self.calls.append('snapshot')
        return self.frames.pop(0) if len(self.frames) > 1 else self.frames[0]

    def tap_install(self, agent_id):
        self.calls.append('install')

    def storage_guidance_visible(self):
        self.calls.append('guidance')
        return self.guidance

    def clock(self):
        return self.time

    def sleep(self, seconds):
        self.time += seconds


class LowStorageTests(unittest.TestCase):
    def setUp(self):
        self.ports = FakePorts()

    def run_proof(self, **kwargs):
        return run_low_storage('fx', self.ports, self.ports,
                               qa_artifact_verified=True, timeout_seconds=2,
                               clock=self.ports.clock, sleep=self.ports.sleep,
                               **kwargs)

    def assert_failure(self, code):
        with self.assertRaisesRegex(LowStorageProofError, '^' + code + '$'):
            self.run_proof()

    def test_plain_storage_guidance_qualifies_actual_app_guard(self):
        result = self.run_proof()
        self.assertEqual(result['state'], 'pass')
        self.assertTrue(result['facts']['visibleStorageWayForward'])
        self.assertTrue(result['facts']['noDownload'])
        self.assertTrue(result['facts']['guardRequirementDeclared'])
        self.assertEqual(result['facts']['declaredRequiredBytes'], QA_MIN_FREE_BYTES)
        self.assertNotIn('requiredBytes', result['facts'])
        self.assertEqual(self.ports.calls[:4],
                         ['storage', 'inventory', 'snapshot', 'install'])

    def test_generic_copy_is_partial(self):
        self.ports.guidance = False
        result = self.run_proof()
        self.assertEqual(result['state'], 'partial')
        self.assertFalse(result['facts']['asserted'])
        self.assertTrue(result['facts']['guardRefused'])

    def test_visible_storage_copy_can_follow_native_poll(self):
        visibility = iter([False, False, True])
        self.ports.storage_guidance_visible = lambda: next(visibility)
        result = self.run_proof()
        self.assertEqual(result['state'], 'pass')
        self.assertEqual(result['facts']['elapsedSeconds'], .5)

    def test_existing_target_refused_before_install(self):
        self.ports.inventory['leftovers'] = True
        self.assert_failure('target_not_absent')
        self.assertNotIn('install', self.ports.calls)

    def test_running_job_refused_before_install(self):
        self.ports.frames[0] = job('old', 'running', 'running')
        self.assert_failure('setup_not_terminal')
        self.assertNotIn('install', self.ports.calls)

    def test_stale_job_never_qualifies(self):
        self.ports.frames = [job('old'), job('old')]
        self.assert_failure('storage_guard_timeout')
        self.assertNotIn('guidance', self.ports.calls)

    def test_any_download_before_failure_refused(self):
        self.ports.frames[1] = job(done=1)
        self.assert_failure('download_started')

    def test_dependency_download_does_not_qualify_global_no_download(self):
        self.ports.frames[1]['components']['paseo'] = {'state': 'done', 'done': 8}
        self.assert_failure('download_started')

    def test_dependency_download_before_target_appears_is_rejected(self):
        dependency_job = {'jobId': 'new', 'state': 'running', 'components': {
            'node': {'state': 'running', 'done': 1}}}
        self.ports.frames.insert(1, dependency_job)
        self.assert_failure('download_started')

    def test_invalid_dependency_counter_is_not_assumed_zero(self):
        self.ports.frames[1]['components']['node'] = {'state': 'skipped', 'done': False}
        self.assert_failure('invalid_download_counter')

    def test_skipped_dependencies_with_no_bytes_can_qualify(self):
        self.ports.frames[1]['components']['node'] = {'state': 'skipped', 'done': None}
        self.ports.frames[1]['components']['paseo'] = {'state': 'skipped', 'done': 0}
        self.assertEqual(self.run_proof()['state'], 'pass')

    def test_unknown_storage_code_does_not_reflect_raw_text(self):
        self.ports.frames[1] = job(errorCode='secret fake native output')
        self.assert_failure('native_storage_code_not_observed')

    def test_normal_threshold_does_not_qualify_injected_guard(self):
        self.ports.frames[1] = job(declaredMinimumFreeBytes=300000000)
        self.assert_failure('qa_requirement_declaration_not_observed')

    def test_absent_declaration_cannot_be_replaced_by_invented_native_field(self):
        self.ports.frames[1] = job(declaredMinimumFreeBytes=None,
                                  requiredFreeBytes=QA_MIN_FREE_BYTES)
        self.assert_failure('qa_requirement_declaration_not_observed')

    def test_successful_install_does_not_qualify_refusal(self):
        self.ports.frames[1] = job(state='done', component_state='done')
        self.assert_failure('guard_did_not_refuse')

    def test_nonterminal_job_is_bounded(self):
        self.ports.frames[1] = job(state='running', component_state='pending')
        self.assert_failure('storage_guard_timeout')

    def test_target_leftovers_after_refusal_are_rejected(self):
        original = self.ports.target_inventory
        count = 0

        def changed(agent_id):
            nonlocal count
            count += 1
            result = original(agent_id).copy()
            if count == 2:
                result['lockPresent'] = True
            return result

        self.ports.target_inventory = changed
        self.assert_failure('target_not_absent')

    def test_real_low_storage_does_not_certify_injection(self):
        self.ports.available = 100
        self.assert_failure('real_storage_not_suitable')
        self.assertEqual(self.ports.calls, ['storage'])

    def test_unverified_qa_artifact_does_not_touch_ports(self):
        with self.assertRaisesRegex(LowStorageProofError,
                                    '^qa_artifact_not_verified$'):
            run_low_storage('fx', self.ports, self.ports,
                            qa_artifact_verified=False)
        self.assertEqual(self.ports.calls, [])

    def test_claude_cannot_be_subject(self):
        with self.assertRaisesRegex(LowStorageProofError, '^unsupported_agent$'):
            run_low_storage('claude', self.ports, self.ports,
                            qa_artifact_verified=True)
        self.assertEqual(self.ports.calls, [])


if __name__ == '__main__':
    unittest.main()
