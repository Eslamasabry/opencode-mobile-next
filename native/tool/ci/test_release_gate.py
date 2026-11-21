#!/usr/bin/env python3
"""Tests for the release gates: scripts/native-release.sh must refuse without a passing Native CI verdict and a passing
device smoke result for HEAD's commit, release_verdict.py reads the verdict the way summary.py's job writes it, and
release_smoke.py still names words the app has.

    python3 -I tool/ci/test_release_gate.py          (run by the `plan` job of native-ci.yml)

The release script runs in a throw-away git repository with a fake `gh` (OC_GH) and a fake version allocator; --no-build
means no Gradle, so only the preflight and the two gates are exercised.
"""
import importlib.util, json, os, re, shutil, subprocess, sys, tempfile, unittest, warnings

warnings.simplefilter("ignore", ResourceWarning)

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..'))

FAKE_GH = r'''#!/usr/bin/env python3
import json, os, sys
runs = json.load(open(os.path.join(os.environ['FAKE_GH_DIR'], 'runs.json')))
a = sys.argv[1:]
if a[:2] == ['run', 'list']:
    print(json.dumps([{'databaseId': r['id'], 'createdAt': r['createdAt']} for r in runs]))
elif a[:2] == ['run', 'download']:
    run = next(r for r in runs if str(r['id']) == a[2])
    if run.get('verdict') is None:
        sys.exit(1)
    d = a[a.index('-D') + 1]
    json.dump(run['verdict'], open(os.path.join(d, 'verdict.json'), 'w'))
else:
    sys.exit(3)
'''


def verdict(sha, exit_=0, lane='candidate', android=True, custom=False, security=True, complete=True, guards_skipped=0):
    return {'sha': sha, 'lane': lane, 'exit': exit_, 'guards_skipped': guards_skipped, 'android_unit': android, 'desktop_tests': True, 'compile': True,
            'custom_tasks': custom, 'security': security, 'candidate_complete': complete}


class ReleaseGate(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        t = self.tmp
        for rel in ('scripts/native-release.sh', 'native/version.properties', 'native/tool/ci/release_verdict.py',
                    'native/tool/qa/check_tested_pins.py'):
            os.makedirs(os.path.join(t, 'work', os.path.dirname(rel)), exist_ok=True)
            shutil.copy(os.path.join(REPO, rel), os.path.join(t, 'work', rel))
        # The release script's tested-pin gate reads the pins: plant pins whose Claude Code and Paseo ARE in their tested
        # lists, so these tests judge the rest of the script (scripts/native-release.sh's own harness proves the gate).
        pins = os.path.join(t, 'work/native/engine/agents/pins/agent-pins.json')
        os.makedirs(os.path.dirname(pins), exist_ok=True)
        json.dump({'agents': [{'id': 'claude', 'recipe': {'version': '1.0.0'}}], 'claudeCodeTestedVersions': ['1.0.0'],
                   'paseo': {'version': '0.1.0'}, 'paseoTestedVersions': ['0.1.0']}, open(pins, 'w'))
        vc = os.path.join(t, 'work/native/tool/version_code.sh')
        # The allocator's answer for a release: the next multiple of ten above the tree's versionCode (a fixed number
        # goes stale as soon as a build number passes it, as 10061 did).
        code = int(re.search(r'^versionCode=(\d+)', open(os.path.join(REPO, 'native/version.properties')).read(), re.M).group(1))
        open(vc, 'w').write(f'#!/bin/sh\necho {(code // 10 + 1) * 10}\n')
        os.chmod(vc, 0o755)
        self.git_env = dict(os.environ, GIT_AUTHOR_NAME='t', GIT_AUTHOR_EMAIL='t@t', GIT_COMMITTER_NAME='t', GIT_COMMITTER_EMAIL='t@t')
        g = lambda *a, cwd=os.path.join(t, 'work'): subprocess.run(['git', *a], cwd=cwd, check=True, capture_output=True, text=True,
                                                                  env=self.git_env)
        self.git = g
        g('init', '-q', '-b', 'master')
        open(os.path.join(t, 'work/.gitignore'), 'w').write('native/build/\n')
        g('add', '-A')
        g('commit', '-q', '-m', 'x')
        g('init', '-q', '--bare', os.path.join(t, 'remote.git'), cwd=t)
        g('remote', 'add', 'origin', os.path.join(t, 'remote.git'))
        g('push', '-q', 'origin', 'master')
        self.sha = g('rev-parse', 'HEAD').stdout.strip()
        # The tree's own versionCode: every owner build bumps it, so a fixed number goes stale.
        self.code = re.search(r'^versionCode=(\d+)', open(os.path.join(REPO, 'native/version.properties')).read(), re.M).group(1)
        os.makedirs(os.path.join(t, 'bin'))
        gh = os.path.join(t, 'bin/gh')
        open(gh, 'w').write(FAKE_GH)
        os.chmod(gh, 0o755)
        self.smoke_dir = os.path.join(t, 'smoke')
        os.makedirs(self.smoke_dir)
        self.runs([])

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def runs(self, runs):
        json.dump(runs, open(os.path.join(self.tmp, 'bin/runs.json'), 'w'))

    def smoke(self, commit=None, code=None, checks='abcd', failed=''):
        r = {'commit': commit or self.sha, 'version_code': int(code or self.code), 'device_model': 'Test Phone', 'time_utc': '2026-01-01T00:00:00Z',
             'checks': {k: {'name': k, 'pass': k not in failed, 'note': ''} for k in checks}}
        r['pass'] = all(c['pass'] for c in r['checks'].values())
        json.dump(r, open(os.path.join(self.smoke_dir, f'smoke-{self.sha}.json'), 'w'))

    def release(self):
        env = dict(os.environ, OC_GH=os.path.join(self.tmp, 'bin/gh'), FAKE_GH_DIR=os.path.join(self.tmp, 'bin'),
                   OC_SMOKE_RESULT_DIR=self.smoke_dir, OC_RELEASE_REMOTE='origin')
        p = subprocess.run(['bash', 'scripts/native-release.sh', '--no-build'], cwd=os.path.join(self.tmp, 'work'), env=env,
                           capture_output=True, text=True)
        return p.returncode, p.stdout + p.stderr

    def green(self, **kw):
        return {'id': 1, 'createdAt': '2026-01-01T10:00:00Z', 'verdict': verdict(self.sha, **kw)}

    def test_B_BUILD_030_release_bump_message_omits_the_daily_skip_marker(self):
        """B-BUILD-030: exercise the real commit command in the fixture, never --go or a build."""
        script = open(os.path.join(REPO, 'scripts/native-release.sh')).read().splitlines()
        start = next((i for i, line in enumerate(script) if line.startswith('  git commit --quiet -m ')), None)
        self.assertIsNotNone(start, 'the release script must create its bump commit')
        command = []
        for line in script[start:]:
            command.append(line)
            if not line.rstrip().endswith('\\'):
                break
        work = os.path.join(self.tmp, 'work')
        with open(os.path.join(work, 'native/version.properties'), 'a') as fh:
            fh.write('# commit-message fixture\n')
        self.git('add', 'native/version.properties')
        env = dict(self.git_env, NAME='9.9.9', NEXT='10020', LINEAGE='public', TAG='v9.9.9+10020')
        p = subprocess.run(['bash', '-euc', '\n'.join(command)], cwd=work, env=env, capture_output=True, text=True)
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        message = self.git('log', '-1', '--format=%B').stdout
        self.assertEqual(message.splitlines()[0], 'build(release): native 9.9.9+10020')
        self.assertNotIn('[skip ci]', message)
        self.assertIn('Release-Tag: v9.9.9+10020', message)
        self.assertEqual(self.git('tag').stdout, '')

    def test_nothing_proved_is_refused_and_both_gates_say_so(self):
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('ci verdict  REFUSED', out)
        self.assertIn('smoke       REFUSED', out)
        self.assertIn('release_smoke.sh', out)

    def test_green_ci_and_passing_smoke_goes_on_and_names_the_run(self):
        self.runs([self.green()])
        self.smoke()
        code, out = self.release()
        self.assertEqual(code, 0, out)
        self.assertIn('ci verdict  PASS, run 1', out)
        self.assertIn('smoke       PASS (Test Phone', out)

    def test_B_CI_301_non_candidate_pass_cannot_qualify_a_release(self):
        """B-CI-301: daily fast/full/custom/goldens/apk PASS does not prove a complete candidate."""
        for lane in ('fast', 'full', 'full (push)', 'goldens', 'apk only', 'custom tasks'):
            with self.subTest(lane=lane):
                self.runs([self.green(lane=lane, custom=(lane == 'custom tasks'))])
                self.smoke()
                code, out = self.release()
                self.assertEqual(code, 1, out)
                self.assertIn('candidate', out)

    def test_B_CI_302_newest_incomplete_candidate_cannot_use_an_older_pass(self):
        """B-CI-302: missing/false completeness evidence refuses the newest candidate."""
        for complete in (None, False):
            with self.subTest(complete=complete):
                missing = verdict(self.sha, complete=complete)
                if complete is None:
                    del missing['candidate_complete']
                self.runs([self.green(), {'id': 2, 'createdAt': '2026-01-02T10:00:00Z', 'verdict': missing}])
                self.smoke()
                code, out = self.release()
                self.assertEqual(code, 1, out)
                self.assertIn('candidate', out)

    def test_B_CI_304_newest_missing_or_mismatched_verdict_cannot_use_an_older_candidate(self):
        """B-CI-304: unknown run coverage cannot silently rescue an older candidate."""
        for proof in (None, verdict(self.sha, lane=''), verdict('0' * 40)):
            with self.subTest(proof=proof):
                self.runs([self.green(), {'id': 2, 'createdAt': '2026-01-02T10:00:00Z', 'verdict': proof}])
                self.smoke()
                code, out = self.release()
                self.assertEqual(code, 1, out)
                self.assertIn('candidate', out)

    def test_a_name_that_already_has_a_release_tag_is_refused_and_the_tag_is_named(self):
        # Every public release needs a version name no earlier release used (tags v<name>+<build>).
        self.runs([self.green()])
        self.smoke()
        name = re.search(r'^versionName=(\S+)', open(os.path.join(REPO, 'native/version.properties')).read(), re.M).group(1)
        work = os.path.join(self.tmp, 'work')
        subprocess.run(['git', 'tag', f'v{name}+10005'], cwd=work, check=True)
        subprocess.run(['git', 'push', '-q', 'origin', f'v{name}+10005'], cwd=work, check=True)
        subprocess.run(['git', 'tag', '-d', f'v{name}+10005'], cwd=work, check=True, capture_output=True)
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn(f'version {name} is already published as v{name}+10005', out)

    def test_green_ci_without_a_smoke_is_refused(self):
        self.runs([self.green()])
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('smoke       REFUSED: no result file', out)

    def test_passing_smoke_without_green_ci_is_refused(self):
        self.smoke()
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('ci verdict  REFUSED', out)

    def test_the_newest_counting_run_decides_an_older_green_run_does_not_rescue_it(self):
        self.runs([self.green(), {'id': 2, 'createdAt': '2026-01-02T10:00:00Z', 'verdict': verdict(self.sha, exit_=1)}])
        self.smoke()
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('FAIL, the newest counting run 2', out)

    def test_a_run_without_the_android_unit_tests_does_not_count(self):
        self.runs([self.green(android=False)])
        self.smoke()
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('incomplete candidate evidence', out)

    def test_a_red_security_suite_refuses_release_even_if_the_aggregate_exit_says_green(self):
        self.runs([self.green(security=False)])
        self.smoke()
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('security', out)

    def test_B_CI_310_a_candidate_with_a_skipped_guard_is_refused_even_if_the_exit_says_green(self):
        """A guard whose reference picture was not fetched passed nothing: the release refuses it, with a plain message."""
        self.runs([self.green(guards_skipped=2)])
        self.smoke()
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('2 guard test(s) skipped', out)
        self.assertIn('references were not fetched', out)

    def test_B_CI_311_a_verdict_without_the_guard_count_cannot_prove_a_candidate(self):
        old = verdict(self.sha)
        del old['guards_skipped']
        self.runs([{'id': 1, 'createdAt': '2026-01-01T10:00:00Z', 'verdict': old}])
        self.smoke()
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('unknown number of guard', out)

    def test_a_newer_missing_security_fact_cannot_fall_back_to_an_older_green_run(self):
        missing = verdict(self.sha)
        del missing['security']
        self.runs([self.green(), {'id': 2, 'createdAt': '2026-01-02T10:00:00Z', 'verdict': missing}])
        self.smoke()
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('security', out)

    def test_a_tasks_only_run_is_skipped_but_an_older_full_run_still_counts(self):
        self.runs([self.green(), {'id': 2, 'createdAt': '2026-01-02T10:00:00Z', 'verdict': verdict(self.sha, exit_=1, lane='custom tasks', custom=True)}])
        self.smoke()
        code, out = self.release()
        self.assertEqual(code, 0, out)
        self.assertIn('run 1', out)

    def test_a_run_for_another_commit_or_without_a_verdict_file_does_not_count(self):
        self.runs([{'id': 1, 'createdAt': '2026-01-01T10:00:00Z', 'verdict': verdict('0' * 40)},
                   {'id': 2, 'createdAt': '2026-01-02T10:00:00Z', 'verdict': None}])
        self.smoke()
        code, out = self.release()
        self.assertEqual(code, 1, out)

    def test_a_smoke_with_one_failed_check_is_refused(self):
        self.runs([self.green()])
        self.smoke(failed='d')
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('failed or missing checks: d', out)

    def test_a_smoke_missing_a_check_is_refused(self):
        self.runs([self.green()])
        self.smoke(checks='abc')
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('failed or missing checks: d', out)

    def test_a_smoke_for_another_build_is_refused(self):
        self.runs([self.green()])
        self.smoke(code='10040')
        code, out = self.release()
        self.assertEqual(code, 1, out)
        self.assertIn('tested versionCode 10040', out)

    def test_the_build_line_runs_the_android_library_tests(self):
        text = open(os.path.join(REPO, 'scripts/native-release.sh')).read()
        self.assertIn('":android:$(basename "$d"):test"', text)
        self.assertIn('"${android_tests[@]}"', text)


class SmokeScript(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        env = {k: v for k, v in os.environ.items() if k != 'OC_SMOKE_SERIAL'}
        spec = importlib.util.spec_from_file_location('release_smoke', os.path.join(HERE, '..', 'qa', 'release_smoke.py'))
        cls.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.mod)

    def test_every_label_it_looks_for_is_still_a_word_of_the_app(self):
        src = open(os.path.join(HERE, '..', 'qa', 'release_smoke.py')).read()
        import re
        keys = set(re.findall(r"word\('([A-Za-z0-9]+)'\)", src)) | set(re.findall(r'word\("([A-Za-z0-9]+)"\)', src))
        self.assertGreaterEqual(len(keys), 10)
        for k in keys:
            self.assertIn(k, self.mod._words, f'release_smoke.py looks for "{k}" which the app no longer has')

    def test_the_persons_own_texts_exclude_the_apps_words_times_and_the_message_box(self):
        xml = ('<hierarchy><node text="Conversations" class="x" bounds="[0,0][1,1]"/><node text="Plan the garden shed trip zq" class="x" bounds="[0,0][1,1]"/>'
               '<node text="2 min ago" class="x" bounds="[0,0][1,1]"/><node text="draft words" class="android.widget.EditText" bounds="[0,0][1,1]"/></hierarchy>')
        import xml.etree.ElementTree as ET
        nodes = [self.mod.Node(e) for e in ET.fromstring(xml).iter('node')]
        self.assertEqual(self.mod.user_texts(nodes), {'Plan the garden shed trip zq'})

    def test_a_row_is_compared_by_its_title_not_its_changing_time(self):
        import xml.etree.ElementTree as ET
        def texts(label):
            xml = f'<hierarchy><node text="{label}" class="x" bounds="[0,0][1,1]"/></hierarchy>'
            return self.mod.user_texts([self.mod.Node(e) for e in ET.fromstring(xml).iter('node')])
        before = texts('Seed for the final smoke: reply with the word kiwi, kiwi, Just now')
        after = texts('Seed for the final smoke: reply with the word kiwi, kiwi, 2 min ago')
        self.assertEqual(before, {'Seed for the final smoke: reply with the word kiwi'})
        self.assertEqual(before, after)

    def test_a_notice_on_the_conversations_screen_is_not_the_persons_text(self):
        import xml.etree.ElementTree as ET
        xml = ('<hierarchy><node text="The app was closed in the background, so OpenCode stopped. I" class="x" bounds="[0,0][1,1]"/>'
               '<node text="Refactor the parser module zq" class="x" bounds="[0,0][1,1]"/></hierarchy>')
        nodes = [self.mod.Node(e) for e in ET.fromstring(xml).iter('node')]
        self.assertEqual(self.mod.user_texts(nodes), {'Refactor the parser module zq'})

    def test_the_send_button_is_send_in_a_conversation_and_start_on_new_conversation(self):
        import xml.etree.ElementTree as ET
        def labels(*texts):
            xml = '<hierarchy>' + ''.join(f'<node text="{t}" class="x" bounds="[0,0][1,1]"/>' for t in texts) + '</hierarchy>'
            return [n.label() for n in self.mod.send_buttons([self.mod.Node(e) for e in ET.fromstring(xml).iter('node')])]
        self.assertEqual(labels('Message', self.mod.word('kitComposerSend')), [self.mod.word('kitComposerSend')])
        self.assertEqual(labels('Ask OpenCode', self.mod.word('upgradeNewStart')), [self.mod.word('upgradeNewStart')])
        with_agent = self.mod.word('chatsNewStartWith').replace('%1$s', 'Codex')
        self.assertEqual(labels(with_agent), [with_agent])
        self.assertEqual(labels('Message', 'Starting'), [])

    def test_it_refuses_without_a_serial_and_writes_no_serial_into_the_result(self):
        env = {k: v for k, v in os.environ.items() if k != 'OC_SMOKE_SERIAL'}
        p = subprocess.run([sys.executable, '-I', os.path.join(HERE, '..', 'qa', 'release_smoke.py'), '--new-apk', __file__], env=env,
                           capture_output=True, text=True)
        self.assertEqual(p.returncode, 2)
        self.assertIn('OC_SMOKE_SERIAL', p.stderr)
        src = open(os.path.join(HERE, '..', 'qa', 'release_smoke.py')).read()
        self.assertNotIn("'serial'", src)


if __name__ == '__main__':
    unittest.main(verbosity=2)
