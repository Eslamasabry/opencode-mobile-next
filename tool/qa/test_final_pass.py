"""Offline orchestration tests: no adb, ffmpeg, APK tools or sleeping."""
from contextlib import contextmanager
import io
import fcntl
from types import SimpleNamespace
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tool.qa import final_pass as batch


class FinalPassTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.args = batch.parser().parse_args(['--execute', '--candidate-apk', '/candidate.apk',
                                               '--candidate-build', '2203', '--date', '2026-10-09'])
        self.events = []
        self.locked = False
        self.build = None

    @contextmanager
    def lock(self):
        self.assertFalse(self.locked)
        self.locked = True
        self.events.append('lock')
        try:
            yield 99
        finally:
            self.events.append('unlock')
            self.locked = False

    def command(self, argv, **kwargs):
        self.assertTrue(self.locked)
        if 'dumpsys' in argv:
            return type('Result', (), {'returncode': 0, 'stdout': f'versionCode={self.build}'.encode()})()
        self.events.append('restore' if argv[-1] == str(self.args.normal_apk) else 'install')
        self.build = 2202 if self.events[-1] == 'restore' else 2203
        return type('Result', (), {'returncode': 0, 'stdout': b''})()

    def dispatch(self, row, config, context):
        self.assertTrue(self.locked)
        self.events.append(row)
        receipt = context.output / (row + '.json')
        receipt.write_text('{}')
        return {'status': 'pass', 'reason': 'verified', 'receipts': [str(receipt)]}

    def execute(self, dispatch=None, command=None):
        return batch.execute(self.args, root=self.root, lock=self.lock, command=command or self.command,
                             verify=lambda _: {'candidate_sha256': 'a'*64, 'normal_sha256': 'b'*64},
                             dispatch=dispatch or self.dispatch)

    def test_dry_run_prints_plan_without_reads_lock_subprocess_or_writes(self):
        with patch.object(batch, 'execute') as execute, patch.object(batch, 'reservation') as lock, \
             patch.object(batch.subprocess, 'run') as command, \
             patch.object(batch, 'load_inputs') as inputs, patch('sys.stdout', new_callable=io.StringIO) as out:
            self.assertEqual(batch.main(['--dry-run']), 0)
            self.assertEqual(json.loads(out.getvalue())['rows'], list(batch.ROWS))
        for fake in (execute, lock, command, inputs):
            fake.assert_not_called()

    def test_fixed_order_one_reservation_and_restore_before_unlock(self):
        self.assertEqual(self.execute(), 0)
        self.assertEqual([event for event in self.events if event in batch.ROWS], list(batch.ROWS))
        self.assertEqual(self.events.count('lock'), 1)
        self.assertEqual(self.events[-2:], ['restore', 'unlock'])
        report = (self.root / 'docs/qa/final-pass-2026-10-09/README.md').read_text()
        self.assertIn('candidate_sha256', report)
        self.assertIn('[receipt 1](ba-install.json)', report)

    def test_upgrade_runs_before_candidate_replacement(self):
        self.args.fast = True
        self.assertEqual(self.execute(), 0)
        self.assertLess(self.events.index('fq9-upgrade'), self.events.index('install'))
        self.assertNotIn('fq9-background', self.events)
        self.assertNotIn('demo', self.events)
        self.assertEqual(self.events[-2:], ['restore', 'unlock'])

    def test_failure_stops_row_but_next_independent_row_runs(self):
        def dispatch(row, config, context):
            if row == batch.ROWS[0]:
                self.events.append(row)
                raise RuntimeError('PRIVATE_ACCOUNT_VALUE')
            return self.dispatch(row, config, context)
        self.assertEqual(self.execute(dispatch), 1)
        self.assertIn(batch.ROWS[-1], self.events)
        report = (self.root / 'docs/qa/final-pass-2026-10-09/README.md').read_text()
        self.assertNotIn('PRIVATE_ACCOUNT_VALUE', report)
        self.assertIn('| fq9-upgrade | fail | driver_failed |', report)

    def test_blocked_row_does_not_prevent_later_checks(self):
        def dispatch(row, config, context):
            if row == 'bb5':
                return {'status': 'blocked', 'reason': 'runner_unavailable', 'receipts': []}
            return self.dispatch(row, config, context)
        self.assertEqual(self.execute(dispatch), 1)
        self.assertIn('demo', self.events)

    def test_install_failure_blocks_device_rows_but_runs_offline_plan_and_restores(self):
        def command(argv, **kwargs):
            result = self.command(argv, **kwargs)
            if self.events[-1] == 'install':
                result.returncode = 1
            return result
        self.assertEqual(self.execute(command=command), 1)
        self.assertEqual([event for event in self.events if event in batch.ROWS], ['fq9-upgrade', 'fb1'])
        self.assertEqual(self.events[-2:], ['restore', 'unlock'])

    def test_restore_failure_is_reported_and_fails_batch(self):
        def command(argv, **kwargs):
            result = self.command(argv, **kwargs)
            if self.events[-1] == 'restore':
                result.returncode = 1
            return result
        self.assertEqual(self.execute(command=command), 1)
        self.assertIn('normal_restore_failed', (self.root/'docs/qa/final-pass-2026-10-09/README.md').read_text())

    def test_interrupt_restores_then_preserves_blocked_rows_and_summary(self):
        def dispatch(row, config, context):
            raise KeyboardInterrupt
        self.assertEqual(self.execute(dispatch), 130)
        self.assertEqual(self.events[-2:], ['restore', 'unlock'])
        self.assertIn('interrupted', (self.root/'docs/qa/final-pass-2026-10-09/README.md').read_text())

    def test_retained_device_work_blocks_later_device_rows_but_runs_plan(self):
        def dispatch(row, config, context):
            if row == 'fq9-background':
                return {'status': 'fail', 'reason': 'cleanup_failed', 'receipts': [],
                        'data': {'safe_to_continue': False}}
            return self.dispatch(row, config, context)
        self.assertEqual(self.execute(dispatch), 1)
        self.assertNotIn('bd7', self.events)
        self.assertIn('fb1', self.events)
        self.assertNotIn('demo', self.events)

    def test_failed_ba_row_with_confirmed_recovery_allows_later_rows(self):
        def dispatch(row, config, context):
            if row == 'ba-install':
                self.events.append(row)
                return {'status': 'fail', 'reason': 'row_not_qualified', 'receipts': [],
                        'data': {'safe_to_continue': True}}
            return self.dispatch(row, config, context)
        self.assertEqual(self.execute(dispatch), 1)
        self.assertIn('ba-removal', self.events)
        self.assertIn('ba-storage-floor', self.events)
        self.assertIn('bd7', self.events)
        self.assertEqual(self.events[-2:], ['restore', 'unlock'])

    def test_lock_failure_writes_blocked_summary_without_device_access(self):
        @contextmanager
        def refused():
            raise batch.BatchError('lock_timeout')
            yield
        with patch.object(batch, 'load_inputs', return_value={}):
            code = batch.execute(self.args, root=self.root, lock=refused,
                command=self.command, verify=lambda _: {}, dispatch=self.dispatch)
        self.assertEqual(code, 1)
        self.assertEqual(self.events, [])
        self.assertIn('lock_unavailable', (self.root/'docs/qa/final-pass-2026-10-09/README.md').read_text())

    def test_adopted_driver_unlock_cannot_release_parent_reservation(self):
        lockfile = self.root/'test.lock'
        with lockfile.open('a') as owner, lockfile.open('a') as contender, \
                patch.object(batch, 'LOCK', lockfile):
            fcntl.flock(owner, fcntl.LOCK_EX)
            ctx = batch.Context(self.root, self.root, self.args, owner.fileno())
            module = SimpleNamespace(LOCK=lockfile, fcntl=fcntl)
            with ctx.adopt_lock(module):
                with module.LOCK.open('a') as child:
                    module.fcntl.flock(child, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    module.fcntl.flock(child, fcntl.LOCK_UN)
                with self.assertRaises(BlockingIOError):
                    fcntl.flock(contender, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.assertIs(module.fcntl, fcntl)
            self.assertEqual(module.LOCK, lockfile)
            fcntl.flock(owner, fcntl.LOCK_UN)
            fcntl.flock(contender, fcntl.LOCK_EX | fcntl.LOCK_NB)

    def test_adopted_lock_rejects_unrelated_descriptor_and_restores_on_error(self):
        lockfile = self.root/'test.lock'
        wrongfile = self.root/'wrong.lock'
        with lockfile.open('a') as owner, wrongfile.open('a') as wrong, \
                patch.object(batch, 'LOCK', lockfile):
            ctx = batch.Context(self.root, self.root, self.args, owner.fileno())
            module = SimpleNamespace(LOCK=lockfile, fcntl=fcntl)
            with self.assertRaises(batch.BatchError):
                with ctx.adopt_lock(module):
                    module.fcntl.flock(wrong, fcntl.LOCK_UN)
            self.assertIs(module.fcntl, fcntl)
            self.assertEqual(module.LOCK, lockfile)

    def test_unchanged_candidate_is_not_reinstalled_for_every_row(self):
        self.assertEqual(self.execute(), 0)
        self.assertEqual(self.events.count('install'), 1)

    def test_driver_restore_reselects_candidate_before_next_device_row(self):
        def dispatch(row, config, context):
            result = self.dispatch(row, config, context)
            if row == 'ba-removal':
                self.build = 2202
            return result
        self.assertEqual(self.execute(dispatch), 0)
        self.assertEqual(self.events.count('install'), 2)

    def test_pass_without_receipt_fails_closed(self):
        self.assertEqual(batch.normalize('bb5', {'status': 'pass', 'reason': 'verified', 'receipts': []})['status'], 'fail')

    def test_failed_normal_version_check_cannot_pass_restore(self):
        def command(argv, **kwargs):
            result = self.command(argv, **kwargs)
            if self.events[-1] == 'restore' and 'dumpsys' in argv:
                result.stdout = b'versionCode=2203'
            return result
        self.assertEqual(self.execute(command=command), 1)
        self.assertIn('normal_restore_failed', (self.root/'docs/qa/final-pass-2026-10-09/README.md').read_text())

    def test_existing_report_is_not_overwritten_and_device_not_touched(self):
        output = self.root/'docs/qa/final-pass-2026-10-09'
        output.mkdir(parents=True)
        (output/'README.md').write_text('previous evidence')
        with self.assertRaises(batch.BatchError):
            self.execute()
        self.assertEqual(self.events, [])


if __name__ == '__main__':
    unittest.main()
