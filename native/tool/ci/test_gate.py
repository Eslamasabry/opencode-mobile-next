#!/usr/bin/env python3
"""Tests for the Native CI gate (test_report.py + summary.py) with planted Gradle logs and JUnit XML.

    python3 -I tool/ci/test_gate.py          (run by the `plan` job of native-ci.yml, so a broken gate fails the run)

Cases: a test-compile failure is FAIL; a strings drift-check failure is FAIL; a selected module with no results is FAIL;
a known test-class failure is PASS; a new test-class failure is FAIL; a drift test failure cannot be absorbed by the
known list; a green run is PASS.
"""
import json, os, subprocess, sys, tempfile, unittest

HERE = os.path.dirname(os.path.abspath(__file__))
PKG = 'io.github.eslamasabry.oc'


def junit(root, module, cls, failing=()):
    d = os.path.join(root, module, 'build', 'test-results', 'desktopTest')
    os.makedirs(d, exist_ok=True)
    cases = ''.join(f'<testcase name="{n}" classname="{cls}"><failure message="boom">x</failure></testcase>' for n in failing)
    cases += f'<testcase name="passes" classname="{cls}"/>'
    with open(os.path.join(d, f'TEST-{cls}.xml'), 'w') as fh:
        fh.write(f'<testsuite name="{cls}">{cases}</testsuite>')


def run_gate(files_log, tasks, xml=(), results=None, report_env=None, planned='0', jobs=None):
    """Plants a shard (log + XML), runs test_report.py then summary.py. -> (exit code, markdown)."""
    with tempfile.TemporaryDirectory() as tmp:
        work, reports = os.path.join(tmp, 'native'), os.path.join(tmp, 'reports')
        os.makedirs(work)
        for module, cls, failing in xml:
            junit(work, module, cls, failing)
        log = os.path.join(tmp, 'gradle.log')
        with open(log, 'w') as fh:
            fh.write(files_log)
        env = dict(os.environ, GRADLE_LOG=log, TASKS=tasks, GRADLE_OUTCOME='failure' if 'FAILED' in files_log else 'success',
                   JOB_STATUS='success', PICK_OUTCOME='success')
        env.update(report_env or {})
        subprocess.run([sys.executable, '-I', os.path.join(HERE, 'test_report.py'), 'fast-0', reports], cwd=work, env=env,
                       check=True, stdout=subprocess.DEVNULL)
        for name, body in (jobs if jobs is not None else {'compile': {'failed': False, 'gradle_ran': True}, 'apk': {'failed': False, 'gradle_ran': True}}).items():
            json.dump(body, open(os.path.join(reports, f'{name}.json'), 'w'))
        env = dict(os.environ, RESULT_TEST='success', RESULT_COMPILE='success', RESULT_APK='success', PLANNED_SHARDS=planned)
        env.update(results or {})
        p = subprocess.run([sys.executable, '-I', os.path.join(HERE, 'summary.py'), reports, os.path.join(tmp, 'nobase'), 'fast'],
                           env=env, capture_output=True, text=True)
        return p.returncode, p.stdout


TEST_FAILED_LOG = """> Task :feature:chats:desktopTest FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':feature:chats:desktopTest'.
> There were failing tests. See the report at: file:///x
"""


class Gate(unittest.TestCase):
    def test_green_run_passes(self):
        code, md = run_gate('> Task :feature:chats:desktopTest\n', ':feature:chats:desktopTest', [('feature/chats', f'{PKG}.feature.chats.XTest', ())])
        self.assertEqual(code, 0, md)
        self.assertIn('## Native CI: PASS', md)

    def test_test_compile_failure_is_fail(self):
        log = ("> Task :feature:settings:compileTestKotlinDesktop FAILED\n"
               "e: file:///native/feature/settings/src/desktopTest/kotlin/A.kt:3:5 Name contains illegal characters: ;\n\n"
               "* What went wrong:\nExecution failed for task ':feature:settings:compileTestKotlinDesktop'.\n"
               "> A failure occurred while executing org.jetbrains.kotlin.compilerRunner.GradleCompilerRunnerWithWorkers$GradleKotlinCompilerWorkAction\n")
        code, md = run_gate(log, ':feature:settings:desktopTest')
        self.assertEqual(code, 1, md)
        self.assertIn('## Native CI: FAIL', md)
        self.assertIn('compileTestKotlinDesktop', md)
        self.assertIn('feature/settings: no test results', md)

    def test_drift_check_failure_is_fail(self):
        log = ("> Task :tools:strings:test FAILED\n* What went wrong:\nExecution failed for task ':tools:strings:test'.\n"
               "> There were failing tests. See the report at: file:///x\n")
        code, md = run_gate(log, ':tools:strings:test', [('tools/strings', f'{PKG}.tools.strings.DriftTest', ('committed ui-words XML matches',))])
        self.assertEqual(code, 1, md)
        self.assertIn('FAIL', md)

    def test_non_test_task_failure_is_fail_even_with_green_tests(self):
        log = ("> Task :ui:words:generateStrings FAILED\n* What went wrong:\nExecution failed for task ':ui:words:generateStrings'.\n> boom\n"
               "> Task :ui:words:desktopTest\n")
        code, md = run_gate(log, ':ui:words:desktopTest', [('ui/words', f'{PKG}.ui.words.PluralsTest', ())])
        self.assertEqual(code, 1, md)
        self.assertIn('generateStrings', md)

    def test_selected_module_with_no_results_is_fail(self):
        code, md = run_gate('> Task :feature:chats:desktopTest\n', ':feature:chats:desktopTest :feature:usage:desktopTest',
                            [('feature/chats', f'{PKG}.feature.chats.XTest', ())])
        self.assertEqual(code, 1, md)
        self.assertIn('feature/usage: no test results', md)

    def test_known_test_class_failure_is_pass(self):
        cls = f'{PKG}.feature.usage.LimitsScreenTest'  # listed in known_failures.txt
        log = TEST_FAILED_LOG.replace('feature:chats', 'feature:usage')
        code, md = run_gate(log, ':feature:usage:desktopTest', [('feature/usage', cls, ('a provider without limits',))])
        self.assertEqual(code, 0, md)
        self.assertIn('known noise', md)

    def test_new_test_class_failure_is_fail(self):
        code, md = run_gate(TEST_FAILED_LOG, ':feature:chats:desktopTest', [('feature/chats', f'{PKG}.feature.chats.BrandNewTest', ('breaks',))])
        self.assertEqual(code, 1, md)
        self.assertIn('BrandNewTest', md)

    def test_known_list_cannot_absorb_a_gate_module(self):
        log = TEST_FAILED_LOG.replace('feature:chats', 'quality')
        code, md = run_gate(log, ':quality:test', [('quality', f'{PKG}.quality.KonsistRulesTest', ('K5 no Log',))])
        self.assertEqual(code, 1, md)

    def test_flutter_gate_failure_is_noise(self):
        cls = f'{PKG}.feature.chats.rb.o41.ChatsGateTest'
        code, md = run_gate(TEST_FAILED_LOG, ':feature:chats:desktopTest', [('feature/chats', cls, ('chats_loaded',))])
        self.assertEqual(code, 0, md)

    def test_failed_compile_job_is_fail(self):
        code, md = run_gate('> Task :feature:chats:desktopTest\n', ':feature:chats:desktopTest', [('feature/chats', f'{PKG}.feature.chats.XTest', ())],
                            results={'RESULT_COMPILE': 'failure'})
        self.assertEqual(code, 1, md)
        self.assertIn('compile job failed', md)


class Integrity(unittest.TestCase):
    """A run must not look green when Gradle never ran (exit 2, not 1)."""
    T = ':feature:chats:desktopTest'

    def test_gradle_never_ran_is_integrity_fail(self):
        code, md = run_gate('Exception in thread "main" java.io.IOException: Server returned HTTP response code: 504\n', self.T)
        self.assertEqual(code, 2, md)
        self.assertIn('INTEGRITY', md)
        self.assertIn('Gradle never ran', md)

    def test_shard_job_that_died_in_setup_is_integrity_fail(self):
        code, md = run_gate('', '', report_env={'JOB_STATUS': 'failure', 'PICK_OUTCOME': 'skipped'})
        self.assertEqual(code, 2, md)
        self.assertIn('did not get as far as running Gradle', md)

    def test_missing_planned_shard_is_integrity_fail(self):
        code, md = run_gate('> Task :feature:chats:desktopTest\n', self.T, [('feature/chats', f'{PKG}.feature.chats.XTest', ())], planned='0,1')
        self.assertEqual(code, 2, md)
        self.assertIn('1 of 2 planned test shard(s) left no report (shard 1)', md)

    def test_compile_job_without_report_is_integrity_fail(self):
        code, md = run_gate('> Task :feature:chats:desktopTest\n', self.T, [('feature/chats', f'{PKG}.feature.chats.XTest', ())],
                            jobs={'apk': {'failed': False, 'gradle_ran': True}})
        self.assertEqual(code, 2, md)
        self.assertIn('compile: the job left no report', md)

    def test_apk_failed_before_gradle_is_integrity_fail(self):
        code, md = run_gate('> Task :feature:chats:desktopTest\n', self.T, [('feature/chats', f'{PKG}.feature.chats.XTest', ())],
                            jobs={'compile': {'failed': False, 'gradle_ran': True}, 'apk': {'failed': True, 'gradle_ran': False}})
        self.assertEqual(code, 2, md)

    def test_all_shards_present_and_ran_passes(self):
        code, md = run_gate('> Task :feature:chats:desktopTest\n', self.T, [('feature/chats', f'{PKG}.feature.chats.XTest', ())], planned='0')
        self.assertEqual(code, 0, md)


if __name__ == '__main__':
    unittest.main(verbosity=2)
