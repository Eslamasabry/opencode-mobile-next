#!/usr/bin/env python3
"""Tests for the Native CI gate (test_report.py + summary.py) with planted Gradle logs and JUnit XML.

    python3 -I tool/ci/test_gate.py          (run by the `plan` job of native-ci.yml, so a broken gate fails the run)

Cases: a test-compile failure is FAIL; a strings drift-check failure is FAIL; a selected module with no results is FAIL;
a known test-class failure is PASS; a new test-class failure is FAIL; a drift test failure cannot be absorbed by the
known list; a green run is PASS.
"""
import json, os, re, shutil, subprocess, sys, tempfile, unittest

HERE = os.path.dirname(os.path.abspath(__file__))
PKG = 'io.github.eslamasabry.oc'


def junit(root, module, cls, failing=(), suite="desktopTest", out=""):
    d = os.path.join(root, module, 'build', 'test-results', suite)
    os.makedirs(d, exist_ok=True)
    cases = ''.join(f'<testcase name="{n}" classname="{cls}"><failure message="boom">x</failure></testcase>' for n in failing)
    cases += f'<testcase name="passes" classname="{cls}"/>'
    with open(os.path.join(d, f'TEST-{cls}.xml'), 'w') as fh:
        fh.write(f'<testsuite name="{cls}">{cases}<system-out><![CDATA[{out}]]></system-out></testsuite>')


def run_gate(files_log, tasks, xml=(), results=None, guard_out='', report_env=None, planned='0', jobs=None, known=None, security=True,
             detekt='clean'):
    """Plants a shard (log + XML), runs test_report.py then summary.py. -> (exit code, markdown)."""
    with tempfile.TemporaryDirectory() as tmp:
        work, reports = os.path.join(tmp, 'native'), os.path.join(tmp, 'reports')
        os.makedirs(work)
        for module, cls, failing in xml:
            junit(work, module, cls, failing, out=guard_out)
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
        if detekt == 'clean':  # detekt_lane.sh's report for touched modules with nothing to say
            detekt = {'ran': True, 'modules': [':core:net'], 'exit': 0, 'ratchet': 'ok', 'findings': {}}
        if detekt is not None:
            json.dump(detekt, open(os.path.join(reports, 'detekt.json'), 'w'))
        if security is not None:
            fact = {'executed': True, 'full': True, 'tests': 60, 'failed': 0 if security else 1, 'skipped': 0}
            json.dump({'name': 'security-proof', 'tests': 60, 'failed': fact['failed'], 'failures': [], 'modules': {}, 'security': fact},
                      open(os.path.join(reports, 'report-security-proof.json'), 'w'))
        env = dict(os.environ, RESULT_TEST='success', RESULT_COMPILE='success', RESULT_APK='success', RESULT_DETEKT='success', PLANNED_SHARDS=planned)
        env.update(results or {})
        summary = os.path.join(HERE, 'summary.py')
        if known is not None:  # a planted known_failures.txt: summary.py reads the list next to itself
            ci = os.path.join(tmp, 'ci')
            os.makedirs(ci)
            shutil.copy(summary, ci)
            open(os.path.join(ci, 'known_failures.txt'), 'w').write(known)
            summary = os.path.join(ci, 'summary.py')
        p = subprocess.run([sys.executable, '-I', summary, reports, os.path.join(tmp, 'nobase'), 'fast'],
                           env=env, capture_output=True, text=True)
        return p.returncode, p.stdout


TEST_FAILED_LOG = """> Task :feature:chats:desktopTest FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':feature:chats:desktopTest'.
> There were failing tests. See the report at: file:///x
"""


class Gate(unittest.TestCase):
    def test_B_CI_305_failed_verification_dependency_is_judged_by_its_assertions(self):
        """B-CI-305: a failed Robolectric Test blocks its verification lifecycle task."""
        log = ("> Task :ui:kit:testAndroidHostTest FAILED\n"
               "Execution failed for task ':ui:kit:testAndroidHostTest'.\n"
               "> There were failing tests. See the report at: file:///x\n")
        code, md = run_gate(log, ':ui:kit:verifyRoborazziAndroidHostTest',
                            [('ui/kit', f'{PKG}.ui.kit.PartADesktopTest', ('N1 B-KIT-029 the title keeps room next to long actions on a phone',))])
        self.assertEqual(code, 0, md)
        self.assertIn('known noise', md)

    def test_B_CI_306_a_new_detekt_finding_fails_the_lane_and_names_the_module(self):
        code, md = run_gate('> Task :core:net:desktopTest\n', ':core:net:desktopTest', [('core/net', f'{PKG}.core.net.XTest', ())],
                            detekt={'ran': True, 'modules': [':core:net', ':engine:analytics'], 'exit': 1, 'ratchet': 'ok',
                                    'findings': {':core:net': 2}})
        self.assertEqual(code, 1, md)
        self.assertIn('## Native CI: FAIL', md)
        self.assertIn('detekt: 2 new finding(s) on :core:net', md)
        self.assertIn('| detekt | FAILED on :core:net, :engine:analytics: :core:net: 2 finding(s)', md)

    def test_B_CI_306_a_clean_detekt_run_is_printed_per_module_and_passes(self):
        code, md = run_gate('> Task :core:net:desktopTest\n', ':core:net:desktopTest', [('core/net', f'{PKG}.core.net.XTest', ())],
                            detekt={'ran': True, 'modules': [':core:net', ':engine:analytics'], 'exit': 0, 'ratchet': 'ok', 'findings': {}})
        self.assertEqual(code, 0, md)
        self.assertIn('| detekt | 0 new findings on :core:net, :engine:analytics (0 old, not counted; ratchet vs base by signature)', md)

    def test_B_CI_306_a_growing_baseline_fails_even_with_no_finding(self):
        code, md = run_gate('> Task :core:net:desktopTest\n', ':core:net:desktopTest', [('core/net', f'{PKG}.core.net.XTest', ())],
                            detekt={'ran': True, 'modules': [':core:net'], 'exit': 1, 'ratchet': 'FAIL', 'findings': {}})
        self.assertEqual(code, 1, md)
        self.assertIn('a baseline grew', md)

    def test_B_CI_306_detekt_that_could_not_run_is_not_a_pass(self):
        code, md = run_gate('> Task :core:net:desktopTest\n', ':core:net:desktopTest', [('core/net', f'{PKG}.core.net.XTest', ())],
                            detekt={'ran': True, 'modules': [':core:net'], 'exit': 2, 'ratchet': 'ok', 'findings': {':core:net': -1}})
        self.assertEqual(code, 1, md)
        self.assertIn('detekt: could not run on :core:net', md)

    def test_B_CI_306_a_fast_run_with_no_detekt_report_is_integrity_not_pass(self):
        code, md = run_gate('> Task :core:net:desktopTest\n', ':core:net:desktopTest', [('core/net', f'{PKG}.core.net.XTest', ())],
                            detekt=None, results={'RESULT_DETEKT': 'skipped'})
        self.assertEqual(code, 2, md)
        self.assertIn('detekt: the lane left no detekt report', md)

    def test_B_CI_309_a_failing_android_variant_test_is_not_a_module_that_never_ran(self):
        """B-CI-309: `:<module>:test` is a lifecycle task; when the variant unit-test task fails it never prints, and the module
        must not read "never ran" while its failing test is named."""
        log = ("> Task :android:linux:testDebugUnitTest FAILED\n\n* What went wrong:\n"
               "Execution failed for task ':android:linux:testDebugUnitTest'.\n"
               "> There were failing tests. See the report at: file:///x\n")
        code, md = run_gate(log, ':android:linux:test',
                            [('android/linux', f'{PKG}.android.linux.InstallFailureTest', ('install fails',))])
        self.assertEqual(code, 1, md)
        self.assertIn('InstallFailureTest', md)
        self.assertNotIn('never ran', md)

    def test_B_CI_309_an_android_module_whose_variant_never_ran_still_reads_never_ran(self):
        code, md = run_gate('> Task :feature:chats:desktopTest\n', ':feature:chats:desktopTest :android:linux:test',
                            [('feature/chats', f'{PKG}.feature.chats.XTest', ())])
        self.assertEqual(code, 1, md)
        self.assertIn('android/linux: no test results', md)

    def test_green_run_passes(self):
        code, md = run_gate('> Task :feature:chats:desktopTest\n', ':feature:chats:desktopTest', [('feature/chats', f'{PKG}.feature.chats.XTest', ())])
        self.assertEqual(code, 0, md)
        self.assertIn('## Native CI: PASS', md)

    def test_B_CI_310_guards_skipped_are_counted_and_printed_on_their_own_line(self):
        """A guard that could not run is never a clean pass: the count is printed, zero or not."""
        xml = [('feature/chats', f'{PKG}.feature.chats.XTest', ())]
        code, md = run_gate('> Task :feature:chats:desktopTest\n', ':feature:chats:desktopTest', xml)
        self.assertIn('\n0 guards skipped (references not fetched)\n', md)
        out = 'OC-GUARD-SKIPPED picture kit_composer_idle_empty_light\nOC-GUARD-SKIPPED picture kit_row_default_light\nnoise\n'
        code, md = run_gate('> Task :feature:chats:desktopTest\n', ':feature:chats:desktopTest', xml, guard_out=out)
        self.assertIn('\n2 guards skipped (references not fetched)\n', md)
        self.assertEqual(code, 0, md)  # a fast or full lane reports it; only a candidate is refused for it

    def test_a_fast_run_whose_compile_and_tests_never_ran_is_integrity_not_pass(self):
        code, md = run_gate('> Task :feature:chats:desktopTest\n', ':feature:chats:desktopTest', [('feature/chats', f'{PKG}.feature.chats.XTest', ())],
                            results={'RESULT_COMPILE': 'skipped', 'RESULT_TEST': 'skipped'})
        self.assertEqual(code, 2, md)
        self.assertIn('## Native CI: FAIL', md)
        self.assertIn('compile: the job did not run', md)

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
        cls = f'{PKG}.feature.usage.LimitsScreenTest'  # a class line, planted: the real list holds none for it any more
        log = TEST_FAILED_LOG.replace('feature:chats', 'feature:usage')
        code, md = run_gate(log, ':feature:usage:desktopTest', [('feature/usage', cls, ('a provider without limits',))],
                            known=f'feature/usage :: {cls} | planted\n')
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


class SecurityGate(unittest.TestCase):
    def test_missing_full_security_evidence_is_integrity_failure(self):
        code, md = run_gate('> Task :quality:test\n', ':quality:test', security=None)
        self.assertEqual(code, 2, md)
        self.assertIn('no executed full :quality:secTest report', md)

    def test_red_security_cannot_be_rescued_by_a_green_aggregate(self):
        code, md = run_gate('> Task :quality:test\n', ':quality:test', security=False)
        self.assertEqual(code, 1, md)
        self.assertIn('security suite has failing', md)

    def test_paired_quality_tasks_do_not_inflate_the_module_shard_count(self):
        r = plan('fast', ':quality:test :quality:secTest :ui:kit:desktopTest')
        self.assertEqual((r['count'], r['total']), ('1', '2'))

    def test_workflow_carries_security_into_full_fast_custom_and_release_gates(self):
        root = os.path.join(HERE, '..', '..', '..')
        workflow = open(os.path.join(root, '.github/workflows/native-ci.yml')).read()
        self.assertIn('tasks=":quality:test :quality:secTest"', workflow)
        self.assertIn('test_report.py custom reports', workflow)
        self.assertIn("'security': security", workflow)
        release = open(os.path.join(root, 'scripts/native-release.sh')).read()
        self.assertIn(':quality:test :quality:secTest -Poc.mergedManifest=required', release)


class SecurityReport(unittest.TestCase):
    def report(self, status='', tasks=':quality:secTest', xml=True):
        with tempfile.TemporaryDirectory() as tmp:
            if xml:
                junit(tmp, 'quality', 'p.SecurityTest', suite='secTest')
            log = os.path.join(tmp, 'gradle.log')
            open(log, 'w').write('> Task :quality:secTest' + (' ' + status if status else '') + '\n')
            env = dict(os.environ, TASKS=tasks, GRADLE_LOG=log)
            subprocess.run([sys.executable, '-I', os.path.join(HERE, 'test_report.py'), 'security', tmp],
                           cwd=tmp, env=env, check=True, stdout=subprocess.DEVNULL)
            return json.load(open(os.path.join(tmp, 'report-security.json')))['security']

    def test_executed_nonempty_full_suite_is_recorded(self):
        self.assertEqual(self.report(), {'executed': True, 'full': True, 'tests': 1, 'failed': 0, 'skipped': 0})

    def test_cache_and_uptodate_cannot_claim_executed_security_tests(self):
        self.assertFalse(self.report('FROM-CACHE')['executed'])
        self.assertFalse(self.report('UP-TO-DATE')['executed'])

    def test_filtered_suite_is_not_full_and_missing_xml_is_not_evidence(self):
        self.assertFalse(self.report(tasks=':quality:secTest --tests p.OneTest')['full'])
        self.assertEqual(self.report(xml=False)['tests'], 0)


class OneTestAtATime(unittest.TestCase):
    """known_failures.txt can name one test; its other tests in the same class still count."""
    CLS = f'{PKG}.feature.chats.TwoFailTest'
    LOG = TEST_FAILED_LOG
    T = ':feature:chats:desktopTest'

    def gate(self, known):
        return run_gate(self.LOG, self.T, [('feature/chats', self.CLS, ('first breaks', 'second breaks'))], known=known)

    def test_listing_one_test_leaves_the_other_failing_red(self):
        code, md = self.gate(f'feature/chats :: {self.CLS} > first breaks | planted\n')
        self.assertEqual(code, 1, md)
        self.assertIn('second breaks', md)
        self.assertNotIn('`feature/chats :: ' + self.CLS + ' > first breaks`', md)

    def test_listing_both_tests_is_noise(self):
        code, md = self.gate(f'feature/chats :: {self.CLS} > first breaks | planted\nfeature/chats :: {self.CLS} > second breaks | planted\n')
        self.assertEqual(code, 0, md)

    def test_a_test_line_is_exact_not_a_pattern(self):
        code, md = self.gate(f'feature/chats :: {self.CLS} > * | planted\n')
        self.assertEqual(code, 1, md)

    def test_a_class_line_still_hides_the_whole_class(self):
        code, md = self.gate(f'feature/chats :: {self.CLS} | planted\n')
        self.assertEqual(code, 0, md)


class KnownFailuresCeiling(unittest.TestCase):
    """known_failures.txt only shrinks: a new or wider line fails here. To delete a line, delete it from the file AND
    from CEILING (the ceiling then stays lower for good)."""
    CEILING = {
        '* :: *.rb.*GateTest',
    }

    # The classes that had a class-level line on the base of the narrowing work (2026-11-21). Frozen: an exact test line
    # may exist only for one of these, so a class that never had a line can not get one by the back door.
    BASE_CLASSES = {
        'engine/runtime :: io.github.eslamasabry.oc.engine.runtime.install.VerbatimScriptsTest',
        'feature/background :: io.github.eslamasabry.oc.feature.background.BackgroundScreenUiTest',
        'feature/chats :: io.github.eslamasabry.oc.feature.chats.rb.o41.ServerSwitcherSheetTest',
        'feature/devservices :: io.github.eslamasabry.oc.feature.devservices.DevServicesScreenTest',
        'feature/devservices :: io.github.eslamasabry.oc.feature.devservices.rb.o47.DevServicesPageTest',
        'feature/files :: io.github.eslamasabry.oc.feature.files.FolderPolishTest',
        'feature/phone :: io.github.eslamasabry.oc.feature.phone.PhoneScreensTest',
        'feature/phonesetup :: io.github.eslamasabry.oc.feature.phonesetup.PhoneSetupScreenTest',
        'feature/servers :: io.github.eslamasabry.oc.feature.servers.PhoneOwnServerMarkTest',
        'feature/updates :: io.github.eslamasabry.oc.feature.updates.UpdatesScreenUiTest',
        'feature/usage :: io.github.eslamasabry.oc.feature.usage.LimitsScreenTest',
        'feature/usage :: io.github.eslamasabry.oc.feature.usage.UsageScreenTest',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.E20S09StateActionsTest',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.Kita1FrameInsetsTest',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.PartADesktopTest',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.S7PolishKitTest',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.rb.o09.KitConsequencesFoldedTest',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.rb.o27.KitTaskSheetParityTest',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.rb.o27.KitTextHaloParityTest',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.rb.walkfix.KitStatusLineStackTest',
    }
    # Exact test lines allowed today (they replace class lines; they can only shrink too).
    EXACT_CEILING = {
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.rb.o09.KitConsequencesFoldedTest > kit_consequences_default',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.rb.o09.KitConsequencesFoldedTest > kit_consequences_default_text2',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.rb.o09.KitConsequencesFoldedTest > kit_consequences_default_text2_1280x800',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.rb.o09.KitConsequencesFoldedTest > kit_consequences_default_1280x800',
        'ui/kit :: io.github.eslamasabry.oc.ui.kit.PartADesktopTest > N1 B-KIT-029 the title keeps room next to long actions on a phone',
    }

    @staticmethod
    def violations(heads, ceiling, base_classes, exact_ceiling):
        """What in `heads` (the lines of known_failures.txt, reason cut off) the list may not hold. A class-level line must
        be on the ceiling. An exact test line ("<module> :: <Class> > <test>") narrows a class line and is allowed only
        when its class had a class-level line on the base AND the line itself is on the exact ceiling; any other new
        line (another class, a wider one, a class that never had a line) is refused."""
        out = set()
        for head in heads:
            if ' > ' in head:
                if head.split(' > ', 1)[0] not in base_classes or head not in exact_ceiling:
                    out.add(head)
            elif head not in ceiling:
                out.add(head)
        return sorted(out)

    @staticmethod
    def entries():
        out = []
        for line in open(os.path.join(HERE, 'known_failures.txt')):
            line = line.strip()
            if line and not line.startswith('#'):
                head, _, reason = line.partition('|')
                out.append((head.strip(), reason.strip()))
        return out

    def test_no_line_was_added_or_widened(self):
        added = self.violations([h for h, _ in self.entries()], self.CEILING, self.BASE_CLASSES, self.EXACT_CEILING)
        self.assertEqual(added, [], 'known_failures.txt may only shrink: fix the failing test instead of listing it. New or '
                                    'changed lines: ' + '; '.join(added))

    def test_the_ceiling_follows_every_deletion(self):
        gone = sorted((self.CEILING | self.EXACT_CEILING) - {h for h, _ in self.entries()})
        self.assertEqual(gone, [], 'these lines were deleted from known_failures.txt: delete them from CEILING in '
                                   'test_gate.py too, so the list cannot grow back: ' + '; '.join(gone))

    def test_every_line_has_a_reason_and_none_sits_in_a_gate_module(self):
        for head, reason in self.entries():
            self.assertTrue(reason, f'{head}: a line needs a reason after "|"')
            mod = head.split(' :: ')[0]
            self.assertNotIn(mod, ('quality', 'tools/strings', 'ui/words', 'core/security', 'ui/gate'),
                             f'{head}: a gate module is never absorbed, the line does nothing')

    def test_the_two_classes_that_were_fixed_are_no_longer_listed(self):
        text = ' '.join(h for h, _ in self.entries())
        self.assertNotIn('ConversationDraftsTest', text)
        self.assertNotIn('E06RequestCardsTest', text)

    NARROW_CLASS = 'ui/kit :: io.github.eslamasabry.oc.ui.kit.PartADesktopTest'
    OTHER_CLASS = 'feature/chats :: io.github.eslamasabry.oc.feature.chats.BrandNewTest'
    EXACT_A = NARROW_CLASS + ' > N1 B-KIT-029 first'
    EXACT_B = NARROW_CLASS + ' > N1 B-KIT-029 second'

    def check(self, heads, exact=()):
        return self.violations(heads, {self.NARROW_CLASS, '* :: *.rb.*GateTest'}, {self.NARROW_CLASS}, set(exact))

    def test_B_CI_314_replacing_a_class_line_by_its_exact_tests_is_a_narrowing(self):
        self.assertEqual(self.check(['* :: *.rb.*GateTest', self.EXACT_A, self.EXACT_B], exact=[self.EXACT_A, self.EXACT_B]), [])

    def test_B_CI_315_a_new_line_for_another_class_is_refused(self):
        refused = self.check(['* :: *.rb.*GateTest', self.EXACT_A, self.OTHER_CLASS], exact=[self.EXACT_A])
        self.assertEqual(refused, [self.OTHER_CLASS])
        exact_elsewhere = self.OTHER_CLASS + ' > breaks'  # an exact line whose class had no class line on the base
        self.assertEqual(self.check([self.EXACT_A, exact_elsewhere], exact=[self.EXACT_A, exact_elsewhere]), [exact_elsewhere])

    def test_B_CI_316_a_widening_is_refused(self):
        extra = self.NARROW_CLASS + ' > N1 B-KIT-029 third'  # more than the audited exact lines
        self.assertEqual(self.check([self.EXACT_A, extra], exact=[self.EXACT_A]), [extra])
        self.assertEqual(self.check([self.NARROW_CLASS + '*']), [self.NARROW_CLASS + '*'])  # a wider pattern for the class

    def test_B_CI_317_a_class_narrowed_to_exact_lines_goes_red_on_a_third_test(self):
        cls = f'{PKG}.ui.kit.PartADesktopTest'
        known = f'ui/kit :: {cls} > first breaks | planted\nui/kit :: {cls} > second breaks | planted\n'
        log = TEST_FAILED_LOG.replace('feature:chats', 'ui:kit')
        code, md = run_gate(log, ':ui:kit:desktopTest', [('ui/kit', cls, ('first breaks', 'second breaks'))], known=known)
        self.assertEqual(code, 0, md)  # the two listed ones stay noise
        code, md = run_gate(log, ':ui:kit:desktopTest', [('ui/kit', cls, ('first breaks', 'second breaks', 'third breaks'))], known=known)
        self.assertEqual(code, 1, md)  # a third test of the class turns the verdict red
        self.assertIn('third breaks', md)
        self.assertNotIn('`ui/kit :: ' + cls + ' > first breaks`', md)

    def test_B_CI_318_a_stale_excuse_line_is_printed_in_the_summary(self):
        cls = f'{PKG}.ui.kit.PartADesktopTest'
        live = f'ui/kit :: {cls} > still breaks | planted live'
        stale_exact = f'ui/kit :: {cls} > was fixed | planted stale'
        stale_class = f'ui/kit :: {PKG}.ui.kit.GoneTest | planted stale class'
        not_run = f'feature/usage :: {PKG}.feature.usage.LimitsScreenTest | planted, its module is not in this run'
        log = TEST_FAILED_LOG.replace('feature:chats', 'ui:kit')
        code, md = run_gate(log, ':ui:kit:desktopTest', [('ui/kit', cls, ('still breaks',))],
                            known='\n'.join([live, stale_exact, stale_class, not_run, '* :: *.rb.*GateTest | picture gates']) + '\n')
        self.assertEqual(code, 0, md)  # a stale line never turns a run red by itself: it is seen, then deleted
        self.assertIn('STALE KNOWN-FAILURE LINE: ' + stale_exact, md)
        self.assertIn('STALE KNOWN-FAILURE LINE: ' + stale_class, md)
        self.assertNotIn(live, md)
        self.assertNotIn('LimitsScreenTest', md)
        self.assertNotIn('GateTest', md)


class AndroidUnit(unittest.TestCase):
    """The Android library unit tests count in the verdict (the android-unit job's own report)."""
    T = ':feature:chats:desktopTest'

    def gate(self, android, result='success'):
        jobs = {'compile': {'failed': False, 'gradle_ran': True}, 'apk': {'failed': False, 'gradle_ran': True}}
        if android is not None:
            jobs['android-unit'] = android
        return run_gate('> Task :feature:chats:desktopTest\n', self.T, [('feature/chats', f'{PKG}.feature.chats.XTest', ())],
                        jobs=jobs, results={'RESULT_ANDROID_UNIT': result} if result else {})

    def test_green_android_tests_pass(self):
        code, md = self.gate({'failed': False, 'gradle_ran': True, 'tests': 40, 'failing': [], 'real': []})
        self.assertEqual(code, 0, md)
        self.assertIn('android unit tests', md)

    def test_failing_android_test_is_fail(self):
        code, md = self.gate({'failed': True, 'gradle_ran': True, 'tests': 40, 'failing': ['android/voice :: a.B > breaks'], 'real': []},
                             result='success')
        self.assertEqual(code, 1, md)
        self.assertIn('android unit tests failed', md)
        self.assertIn('android/voice :: a.B > breaks', md)

    def test_failed_android_job_is_fail_even_without_a_report_flag(self):
        code, md = self.gate({'failed': False, 'gradle_ran': True}, result='failure')
        self.assertEqual(code, 1, md)

    def test_android_job_that_never_ran_gradle_is_integrity_fail(self):
        code, md = self.gate({'failed': True, 'gradle_ran': False})
        self.assertEqual(code, 2, md)

    def test_android_job_without_report_is_integrity_fail(self):
        code, md = self.gate(None)
        self.assertEqual(code, 2, md)

    def test_a_run_without_the_android_job_is_unchanged(self):
        code, md = self.gate(None, result='skipped')
        self.assertEqual(code, 0, md)


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


def plan(lane, selection):
    p = subprocess.run(['bash', os.path.join(HERE, 'plan_shards.sh'), lane, selection], capture_output=True, text=True, check=True)
    return dict(line.split('=', 1) for line in p.stdout.splitlines())


class Plan(unittest.TestCase):
    """The shard plan: 8 module shards by default, and the fast lane always has a last shard for :quality:test."""

    def test_fast_fallback_to_all_has_eight_shards_and_a_quality_shard(self):
        r = plan('fast', 'ALL')
        self.assertEqual((r['mode'], r['count'], r['total']), ('all', '8', '9'))

    def test_full_lane_has_eight_shards_and_no_quality_shard(self):
        r = plan('full', 'ALL')
        self.assertEqual((r['mode'], r['count'], r['total']), ('all', '8', '8'))

    def test_fast_selection_without_quality_still_gets_a_quality_shard(self):
        r = plan('fast', ':ui:kit:desktopTest :feature:chat:desktopTest')
        self.assertEqual((r['mode'], r['count'], r['total']), ('list', '2', '3'))

    def test_fast_selection_with_quality_does_not_count_it_as_a_module_shard(self):
        r = plan('fast', ' '.join([':quality:test'] + [f':m{i}:desktopTest' for i in range(7)]))
        self.assertEqual((r['count'], r['total']), ('4', '5'))

    def test_large_fast_selection_gets_eight_shards(self):
        r = plan('fast', ' '.join(f':m{i}:desktopTest' for i in range(20)))
        self.assertEqual((r['count'], r['total']), ('8', '9'))

    def test_never_more_module_shards_than_tasks(self):
        r = plan('fast', ':ui:kit:desktopTest')
        self.assertEqual((r['count'], r['total']), ('1', '2'))

    def test_quality_only_selection_is_one_shard(self):
        r = plan('fast', ':quality:test')
        self.assertEqual((r['count'], r['total']), ('0', '1'))

    def test_empty_selection_runs_only_the_quality_shard(self):
        r = plan('fast', '')
        self.assertEqual((r['mode'], r['count'], r['total']), ('list', '0', '1'))

    def test_B_CI_303_candidate_lane_has_eight_module_shards_and_a_dedicated_quality_shard(self):
        r = plan('candidate', 'ALL')
        self.assertEqual((r['mode'], r['count'], r['total']), ('all', '8', '9'))


class Workflow(unittest.TestCase):
    """The summary job's exit code is the run's verdict on every branch."""

    def test_summary_exits_with_the_verdict_on_work_branches_too(self):
        text = open(os.path.join(HERE, '..', '..', '..', '.github', 'workflows', 'native-ci.yml')).read()
        step = text[text.index('python3 -I native/tool/ci/summary.py'):]
        self.assertIn('exit "$rc"', step)
        self.assertNotIn('WORK_BRANCH', text)

    def test_android_unit_tests_run_by_default_and_count_in_the_verdict(self):
        text = open(os.path.join(HERE, '..', '..', '..', '.github', 'workflows', 'native-ci.yml')).read()
        inp = text[text.index('      android_unit:'):]
        self.assertRegex(inp[:inp.index('runner:')], r'default: true')
        job = text[text.index('  android-unit:'):text.index('  summary:')]
        self.assertIn("github.event_name == 'push' || inputs.android_unit == true", job)
        summary_job = text[text.index('  summary:'):]
        self.assertRegex(summary_job[:summary_job.index('steps:')], r'needs: \[[^\]]*android-unit')
        self.assertIn('RESULT_ANDROID_UNIT: ${{ needs.android-unit.result }}', summary_job)

    def test_the_summary_job_uploads_the_verdict_the_release_script_reads(self):
        text = open(os.path.join(HERE, '..', '..', '..', '.github', 'workflows', 'native-ci.yml')).read()
        self.assertIn('name: ci-verdict', text)
        self.assertIn('> verdict.json', text)

    def test_fast_lane_quality_shard_does_not_depend_on_the_module_list(self):
        text = open(os.path.join(HERE, '..', '..', '..', '.github', 'workflows', 'native-ci.yml')).read()
        self.assertIn('[ "$LANE" = fast ] && [ "${{ matrix.shard }}" = "$COUNT" ]; then', text)



class CandidateGate(unittest.TestCase):
    """B-CI-303: one candidate must prove every required check, not just a green aggregate."""

    def gate(self, change=None):
        desktop = [':feature:chats:desktopTest', ':quality:test', ':quality:secTest']
        android = [':androidApp:testPreviewDebugUnitTest']
        def report(name, tasks):
            return {'name': name, 'lane': 'candidate', 'sha': 'a' * 40, 'tests': 3, 'failed': 0, 'failures': [],
                    'modules': {}, 'planned': tasks, 'gradle_ran': True,
                    'task_status': {t: 'RAN' for t in tasks},
                    'candidate': {'fresh': True, 'unfiltered': True, 'pictures': True, 'picture_tests': 1},
                    'quality': {'executed': True, 'full': True, 'tests': 1, 'failed': 0, 'skipped': 0},
                    'security': {'executed': True, 'full': True, 'tests': 1, 'failed': 0, 'skipped': 0}}
        files = {'report-candidate-0.json': report('candidate-0', desktop),
                 'report-android-unit.json': report('android-unit', android),
                 'android-unit.json': {'failed': False, 'gradle_ran': True, 'tests': 3},
                 'report-build-logic.json': report('build-logic', [':convention:test']),
                 'compile.json': {'failed': False, 'gradle_ran': True},
                 'detekt.json': {'ran': True, 'modules': [':core:net'], 'exit': 0, 'ratchet': 'ok', 'findings': {}},
                 'candidate-plan.json': {'lane': 'candidate', 'sha': 'a' * 40, 'desktop_tasks': desktop.copy(),
                                         'android_tasks': android.copy(),
                                         'self_tests': {n: {'tests': 5, 'passed': True} for n in ('ci', 'release', 'release_tools')}}}
        if change:
            change(files)
        with tempfile.TemporaryDirectory() as tmp:
            for name, body in files.items():
                json.dump(body, open(os.path.join(tmp, name), 'w'))
            # The host ci-verdict wrapper does not pass RESULT_ANDROID_UNIT or RESULT_PLAN.
            env = dict(os.environ, RESULT_COMPILE='success', RESULT_TEST='success', PLANNED_SHARDS='0')
            env.pop('RESULT_ANDROID_UNIT', None)
            p = subprocess.run([sys.executable, '-I', os.path.join(HERE, 'summary.py'), tmp, tmp + '/base', 'candidate'],
                               env=env, capture_output=True, text=True)
            return p.returncode, p.stdout

    def test_B_CI_303_complete_candidate_passes(self):
        code, text = self.gate()
        self.assertEqual(code, 0, text)
        self.assertIn('| android unit tests | ok (3 run, 0 failing) |', text)

    def test_B_CI_310_a_candidate_with_a_skipped_guard_is_not_a_pass(self):
        code, text = self.gate()
        self.assertEqual(code, 0, text)
        self.assertIn('0 guards skipped (references not fetched)', text)
        code, text = self.gate(lambda f: f['report-candidate-0.json'].update(guards_skipped=3))
        self.assertEqual(code, 1, text)
        self.assertIn('## Native CI: FAIL', text)
        self.assertIn('3 guards skipped (references not fetched)', text)
        self.assertIn('3 guard test(s) skipped', text)

    def test_B_CI_303_missing_or_stale_candidate_evidence_refuses_pass(self):
        mutations = {
            'plan': lambda f: f.pop('candidate-plan.json'),
            'module': lambda f: f['candidate-plan.json']['desktop_tasks'].append(':feature:missing:desktopTest'),
            'android': lambda f: f.pop('report-android-unit.json'),
            'build-logic': lambda f: f.pop('report-build-logic.json'),
            'compile-all-tests': lambda f: f['compile.json'].update(compile_all_tests=False),
            'quality': lambda f: f['report-candidate-0.json'].pop('quality'),
            'self-tests': lambda f: f['candidate-plan.json']['self_tests'].pop('release_tools'),
            'self-tests-red': lambda f: f['candidate-plan.json']['self_tests']['release'].update(passed=False),
            'sha': lambda f: f['report-candidate-0.json'].update(sha='b' * 40),
            'cached': lambda f: f['report-candidate-0.json']['candidate'].update(fresh=False),
            'filtered': lambda f: f['report-candidate-0.json']['candidate'].update(unfiltered=False),
            'pictures-off': lambda f: f['report-candidate-0.json']['candidate'].update(pictures=False),
            'no-pictures': lambda f: [r['candidate'].update(picture_tests=0) for n, r in f.items() if n.startswith('report-')],
        }
        for name, mutation in mutations.items():
            with self.subTest(missing=name):
                code, text = self.gate(mutation)
                self.assertEqual(code, 2, text)
                self.assertIn('candidate', text)


if __name__ == '__main__':
    unittest.main(verbosity=2)
