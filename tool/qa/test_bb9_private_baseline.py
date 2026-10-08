"""Mock only: extract the reviewed private bootstrap without executing its device session."""
import ast
from pathlib import Path
from types import SimpleNamespace
import unittest
import xml.etree.ElementTree as ET

SOURCE = Path(__file__).resolve().parents[2] / 'docs/qa/BB9-2026-10-07/service-normal-restore-session.py'


class Refused(Exception):
    pass


class BaselineTests(unittest.TestCase):
    source = SOURCE.read_text()

    def call(self, failures, retained=False, cleanup_failure=False):
        calls = []
        def restore(*unused):
            calls.append('start')
            if failures:
                raise Refused(failures.pop(0))
        h = SimpleNamespace(NATIVE='fixed', FIXTURE='fixture', Q=SimpleNamespace(Refused=Refused, restore_person=restore))
        namespace = dict(H=h, ET=ET, time=SimpleNamespace(sleep=lambda *unused: None), base_bootstrap=lambda *unused: calls.append('bootstrap'))
        node = next(n for n in ast.parse(self.source).body
                    if isinstance(n, ast.FunctionDef) and n.name == 'canonical_bootstrap')
        exec(compile(ast.Module(body=[node], type_ignores=[]), 'private-bootstrap-mock', 'exec'), namespace)
        warmed = [False]
        def warm():
            warmed[0] = True
            calls.append('app')
        def cat(*unused, **kwargs):
            if not warmed[0]:raise Refused('private_snapshot_unavailable')
            return '<map />' if unused[0] != 'fixture' or retained else None
        def instrument(step):
            self.assertEqual(step, 'bb9Cleanup')
            calls.append('cleanup')
            if cleanup_failure:raise Refused('cleanup_refused')
        device=SimpleNamespace(instrument=instrument,ensure_normal_app=warm,cat=cat,adb=lambda *unused,**kwargs:SimpleNamespace(returncode=0))
        return lambda: namespace['canonical_bootstrap'](device, None, []), calls

    def test_retained_fixture_needs_native_cleanup_before_any_new_start(self):
        invoke, calls = self.call([], retained=True)
        invoke()
        self.assertEqual(calls, ['bootstrap', 'app', 'cleanup', 'start'])

    def test_cleanup_refusal_cannot_begin_a_new_fixture_or_start(self):
        invoke, calls = self.call([], retained=True, cleanup_failure=True)
        with self.assertRaisesRegex(Refused, 'cleanup_refused'):
            invoke()
        self.assertEqual(calls, ['bootstrap', 'app', 'cleanup'])

    def test_one_specific_refusal_allows_one_fresh_attempt(self):
        invoke, calls = self.call(['person_start_did_not_rearm_recipe'])
        invoke()
        self.assertEqual(calls, ['bootstrap', 'app', 'start', 'start'])

    def test_other_refusal_is_never_retried(self):
        invoke, calls = self.call(['unrelated_refusal', 'unrelated_refusal'])
        with self.assertRaisesRegex(Refused, 'unrelated_refusal'):
            invoke()
        self.assertEqual(calls, ['bootstrap', 'app', 'start'])

    def test_second_specific_refusal_cannot_claim_a_baseline(self):
        invoke, calls = self.call(['person_start_did_not_rearm_recipe'] * 2)
        with self.assertRaisesRegex(Refused, 'person_start_did_not_rearm_recipe'):
            invoke()
        self.assertEqual(calls, ['bootstrap', 'app', 'start', 'start'])


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['--red']:
        original = BaselineTests.source
        for fragment, test in [
            (' device.ensure_normal_app()\n', 'test_one_specific_refusal_allows_one_fresh_attempt'),
            ("  device.instrument('bb9Cleanup')\n", 'test_retained_fixture_needs_native_cleanup_before_any_new_start'),
            ("str(error) != 'person_start_did_not_rearm_recipe' or ", 'test_other_refusal_is_never_retried'),
            (' or attempt != 0', 'test_second_specific_refusal_cannot_claim_a_baseline'),
        ]:
            assert original.count(fragment) == 1
            BaselineTests.source = original.replace(fragment, '')
            result = unittest.TestResult()
            BaselineTests(test).run(result)
            assert not result.wasSuccessful(), 'removed bootstrap guard unexpectedly passed'
            print('PASS removed_guard_failed ' + test)
        BaselineTests.source = original
        result = unittest.TextTestRunner().run(unittest.defaultTestLoader.loadTestsFromTestCase(BaselineTests))
        raise SystemExit(0 if result.wasSuccessful() else 1)
    unittest.main()
