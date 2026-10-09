"""Offline adapter admission/receipt tests; every device operation is mocked."""
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from tool.qa import final_pass_install as subject


class FinalPassInstallTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = Path(__file__).resolve().parents[2]
        self.lock = self.base / 'emulator.lock'
        self.handle = self.lock.open('a')
        self.addCleanup(self.handle.close)
        self.lock_patch = patch.object(subject, '_LOCK', self.lock)
        self.lock_patch.start()
        self.addCleanup(self.lock_patch.stop)
        self.candidate = self.base / 'candidate.apk'
        self.candidate.write_bytes(b'offline candidate fixture')
        self.context = SimpleNamespace(
            root=self.root, output=self.base / 'output', candidate=self.candidate,
            candidate_build=2203, normal_apk=self.candidate, normal_build=2198,
            lock_fd=self.handle.fileno(), run_id='offline-fixture',
            command=Mock(side_effect=AssertionError('unexpected subprocess')),
            capture=Mock(side_effect=lambda callback: callback()),
            adopt_lock=Mock())

    def artifact(self):
        return {'apk': str(self.candidate), 'build': self.context.candidate_build,
                'sha256': hashlib.sha256(self.candidate.read_bytes()).hexdigest(),
                'sourceRevision': '1' * 40, 'dartDefines': {}}

    def assert_no_execution(self):
        self.context.command.assert_not_called()
        self.context.capture.assert_not_called()
        self.context.adopt_lock.assert_not_called()

    def test_missing_ba_config_is_blocked_without_driver(self):
        for row in ('ba-install', 'ba-removal', 'ba-storage-floor'):
            with self.subTest(row=row):
                result = subject.run(row, {}, self.context)
                self.assertEqual(result, {'status': 'blocked',
                    'reason': 'invalid_configuration', 'receipts': []})
        self.assert_no_execution()

    def test_incompatible_manifest_build_path_or_hash_never_invokes_driver(self):
        for mismatch in ('build', 'apk', 'sha256'):
            with self.subTest(mismatch=mismatch):
                artifact = self.artifact()
                if mismatch == 'build':
                    artifact['build'] = 2199
                elif mismatch == 'apk':
                    other = self.base / 'other.apk'
                    other.write_bytes(self.candidate.read_bytes())
                    artifact['apk'] = str(other)
                else:
                    artifact['sha256'] = '0' * 64
                manifest = self.base / 'artifacts.json'
                manifest.write_text(json.dumps({'normal': artifact}))
                result = subject.run('ba-removal',
                    {'agent': 'fx', 'manifest': str(manifest)}, self.context)
                self.assertEqual(result['status'], 'blocked')
                self.assertEqual(result['reason'], 'candidate_incompatible')
                self.assert_no_execution()

    def test_absent_latest_install_driver_is_blocked(self):
        self.context.root = self.base / 'empty-checkout'
        result = subject.run('ba-install', {'agent': 'fx', 'manifest': '/not/read.json'}, self.context)
        self.assertEqual(result, {'status': 'blocked',
            'reason': 'install_receipt_unavailable', 'receipts': []})
        self.assert_no_execution()

    def test_multiple_agents_stop_after_first_non_pass_and_keep_receipts(self):
        first = self.base / 'first.json'
        second = self.base / 'second.json'
        first.write_text('{}')
        second.write_text('{}')
        outcomes = [
            {'status': 'pass', 'reason': 'verified', 'receipts': [str(first)]},
            {'status': 'fail', 'reason': 'row_not_qualified', 'receipts': [str(second)]},
        ]
        with patch.object(subject, '_generic_ba', side_effect=outcomes) as branch:
            result = subject.run('ba-removal',
                {'agents': ['fx', 'codex', 'gemini'], 'manifest': '/private/receipt.json'},
                self.context)
        self.assertEqual(result, {'status': 'fail', 'reason': 'stopped_on_agent_failure',
            'receipts': [str(first), str(second)], 'data': {'completedAgents': 1}})
        self.assertEqual(branch.call_count, 2)
        self.assertEqual([call.args[1]['agent'] for call in branch.call_args_list], ['fx', 'codex'])
        self.assert_no_execution()

    def test_agent_list_rejects_duplicates_unknown_empty_and_mixed_selection(self):
        configs = [{'agents': []}, {'agents': ['fx', 'fx']},
                   {'agents': ['claude']}, {'agents': ['fx'], 'agent': 'fx'},
                   {'agents': 'fx'}, {'agents': [None]}]
        with patch.object(subject, '_generic_ba') as branch:
            for config in configs:
                with self.subTest(config=config):
                    result = subject.run('ba-removal', config, self.context)
                    self.assertEqual(result['status'], 'blocked')
                    self.assertEqual(result['reason'], 'invalid_configuration')
            branch.assert_not_called()
        self.assert_no_execution()

    def test_agent_failure_preserves_confirmed_continuation_for_later_rows(self):
        with patch.object(subject, '_install', return_value={
                'status': 'fail', 'reason': 'row_not_qualified', 'receipts': [],
                'data': {'safe_to_continue': True}}):
            result = subject.run('ba-install',
                {'agents': ['fx', 'codex'], 'manifest': '/unused'}, self.context)
        self.assertEqual(result['status'], 'fail')
        self.assertIs(result['data']['safe_to_continue'], True)

    def test_incomplete_or_malformed_recovery_facts_do_not_confirm_continuation(self):
        for facts in (None, True, [], {'confirmed': True},
                      {'confirmed': True, 'normalVerified': True, 'setupIdle': True,
                       'targetsAbsent': False, 'storageAvailable': True}):
            with self.subTest(facts=facts):
                self.assertFalse(subject._continuation_confirmed({'continuation': facts}))

    def test_install_continuation_requires_explicit_recovery_not_only_restored_apk(self):
        manifest = self.base / 'manifest.json'
        manifest.write_text('{}')
        receipt = self.context.output / 'ba-install/fx-device.json'
        module = SimpleNamespace(manifest=SimpleNamespace(load=Mock(
            return_value={'normal': self.artifact()})))
        @contextmanager
        def driver(_):
            yield module
        for confirmed in (False, True):
            with self.subTest(confirmed=confirmed):
                def command(*args, **kwargs):
                    receipt.parent.mkdir(parents=True, exist_ok=True)
                    receipt.write_text(json.dumps({'agentId': 'fx', 'appBuild': 2203,
                        'normalRestored': True, 'errorType': 'RuntimeError',
                        'continuation': {'confirmed': confirmed, 'normalVerified': True,
                            'setupIdle': True, 'targetsAbsent': True, 'storageAvailable': True}}))
                    return SimpleNamespace(returncode=1)
                self.context.command.side_effect = command
                with patch.object(subject, '_driver', driver):
                    result = subject.run('ba-install',
                        {'agent': 'fx', 'manifest': str(manifest)}, self.context)
                self.assertEqual(result['status'], 'fail')
                self.assertIs(result['data']['safe_to_continue'], confirmed)

    def test_bb5_wrong_build_is_blocked_before_subprocess_or_artifact_reads(self):
        self.context.root = self.base / 'bb5-checkout'
        directory = self.context.root / 'tool/qa'
        directory.mkdir(parents=True)
        (directory / 'bb5_runtime_acceptance.py').write_text('VERSION = 2198\n')
        config = {key: '/missing/private-input' for key in
                  ('runner_apk', 'normal_apk', 'normal_sidecar', 'apksigner', 'aapt')}
        config.update(qa_apk='/missing/private-qa.apk', target_sha='a' * 64, runner_sha='b' * 64,
                      normal_sha='c' * 64, normal_version=2198)
        result = subject.run('bb5', config, self.context)
        self.assertEqual(result, {'status': 'blocked',
            'reason': 'candidate_incompatible', 'receipts': []})
        self.assert_no_execution()

    def test_bb5_uses_distinct_reviewed_qa_artifact_and_restores_normal(self):
        self.context.candidate_build = self.context.normal_build = 2202
        qa = self.base / 'qa.apk'
        qa.write_bytes(b'QA hooks enabled')
        runner = self.base / 'runner.apk'
        runner.write_bytes(b'instrumentation')
        config = dict(qa_apk=str(qa), runner_apk=str(runner),
            target_sha=hashlib.sha256(qa.read_bytes()).hexdigest(),
            runner_sha=hashlib.sha256(runner.read_bytes()).hexdigest(),
            normal_apk=str(self.candidate), normal_sidecar=str(self.candidate),
            normal_sha=hashlib.sha256(self.candidate.read_bytes()).hexdigest(),
            normal_version=2202, apksigner=str(runner), aapt=str(runner))
        self.context.output.mkdir()
        def command(argv, **kwargs):
            self.assertEqual(argv[argv.index('--apk') + 1], str(qa))
            self.assertNotIn('--qa-apk', argv)
            (self.context.output / 'bb5.txt').write_text(
                'PASS BB5_locked_actual_idle_stop_resume_and_normal_2202_restoration\n')
            return SimpleNamespace(returncode=0)
        self.context.command.side_effect = command
        result = subject.run('bb5', config, self.context)
        self.assertEqual(result['status'], 'pass')

    def generic_fixture(self, row, extra):
        """The current run.py result plus the current uninstall/guard shapes."""
        case = 'uninstall' if row == 'ba-removal' else 'low-storage'
        output = self.context.output / row
        receipt = output / ('fx-' + case + '-observations.json')
        manifest_path = self.base / 'manifest.json'
        manifest_path.write_text('{}')
        result = {'agentId': 'fx', 'case': case, 'appBuild': 2203,
                  'sourceRevision': '1' * 40, 'normalRestored': True,
                  'continuation': {'confirmed': True, 'normalVerified': True,
                      'setupIdle': True, 'targetsAbsent': True, 'storageAvailable': True},
                  'uninstall': {'state': 'pass', 'code': 'verified', 'facts': {
                      'asserted': True, 'removedViaApp': True,
                      'leftoversRemoved': True, 'noOrphans': True,
                      'notInstalledRow': True, 'bytesFreed': 4096,
                      'freeSpaceDeltaBytes': 4096, 'elapsedSeconds': 1}}}
        result.update(extra)

        def main():
            output.mkdir(parents=True)
            receipt.write_text(json.dumps(result))

        module = SimpleNamespace(main=Mock(side_effect=main),
            manifest=SimpleNamespace(load=Mock(return_value={'normal': self.artifact()})))

        @contextmanager
        def driver(_root):
            yield module

        @contextmanager
        def adopt(_module):
            yield

        self.context.adopt_lock.side_effect = adopt
        return driver, module, receipt, {'agent': 'fx', 'manifest': str(manifest_path)}

    def test_current_removal_receipt_schema_is_sufficient(self):
        driver, module, receipt, config = self.generic_fixture('ba-removal', {})
        with patch.object(subject, '_driver', driver):
            result = subject.run('ba-removal', config, self.context)
        self.assertEqual(result, {'status': 'pass', 'reason': 'verified',
                                  'receipts': [str(receipt)]})
        module.main.assert_called_once()
        self.context.command.assert_not_called()

    def test_removal_failure_needs_confirmed_clean_state_to_continue(self):
        driver, _, _, config = self.generic_fixture('ba-removal',
            {'error': 'RuntimeError', 'continuation': {'confirmed': False}})
        with patch.object(subject, '_driver', driver):
            result = subject.run('ba-removal', config, self.context)
        self.assertEqual(result['status'], 'fail')
        self.assertIs(result['data']['safe_to_continue'], False)

    def test_storage_receipt_requires_visible_guidance_as_well_as_native_refusal(self):
        extra = {'guard': {'state': 'pass', 'code': 'verified', 'facts': {
            'asserted': True, 'guardRefused': True, 'noDownload': True,
            'freshJob': True, 'targetAbsent': True, 'visibleStorageWayForward': True,
            'guardRequirementDeclared': True, 'declaredRequiredBytes': 8589934592}},
            'normalRetry': {'pinMatches': True, 'linkMatches': True, 'targetPids': []}}
        driver, module, receipt, config = self.generic_fixture('ba-storage-floor', extra)
        with patch.object(subject, '_driver', driver):
            result = subject.run('ba-storage-floor', config, self.context)
        self.assertEqual(result['status'], 'pass')
        self.assertEqual(result['receipts'], [str(receipt)])
        receipt.unlink()
        # Native refusal alone does not qualify the visible product outcome.
        extra['guard']['facts']['visibleStorageWayForward'] = False
        driver, module, receipt, config = self.generic_fixture('ba-storage-floor', extra)
        # This is a second independent mocked run, with its own fresh output.
        receipt.parent.rmdir()
        with patch.object(subject, '_driver', driver):
            result = subject.run('ba-storage-floor', config, self.context)
        self.assertEqual(result['status'], 'fail')
        self.assertEqual(result['reason'], 'row_not_qualified')
        self.context.command.assert_not_called()


if __name__ == '__main__':
    unittest.main()
