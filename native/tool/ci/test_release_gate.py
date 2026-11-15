#!/usr/bin/env python3
"""Tests for the release gates: scripts/native-release.sh must refuse without a passing Native CI verdict and a passing
device smoke result for HEAD's commit, release_verdict.py reads the verdict the way summary.py's job writes it, and
release_smoke.py still names words the app has.

    python3 -I tool/ci/test_release_gate.py          (run by the `plan` job of native-ci.yml)

The release script runs in a throw-away git repository with a fake `gh` (OC_GH) and a fake version allocator; --no-build
means no Gradle, so only the preflight and the two gates are exercised.
"""
import importlib.util, json, os, shutil, subprocess, sys, tempfile, unittest, warnings

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


def verdict(sha, exit_=0, lane='fast', android=True, custom=False):
    return {'sha': sha, 'lane': lane, 'exit': exit_, 'android_unit': android, 'desktop_tests': True, 'compile': True,
            'custom_tasks': custom}


class ReleaseGate(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        t = self.tmp
        for rel in ('scripts/native-release.sh', 'native/version.properties', 'native/tool/ci/release_verdict.py'):
            os.makedirs(os.path.join(t, 'work', os.path.dirname(rel)), exist_ok=True)
            shutil.copy(os.path.join(REPO, rel), os.path.join(t, 'work', rel))
        vc = os.path.join(t, 'work/native/tool/version_code.sh')
        open(vc, 'w').write('#!/bin/sh\necho 10060\n')
        os.chmod(vc, 0o755)
        g = lambda *a, cwd=os.path.join(t, 'work'): subprocess.run(['git', *a], cwd=cwd, check=True, capture_output=True, text=True,
                                                                  env=dict(os.environ, GIT_AUTHOR_NAME='t', GIT_AUTHOR_EMAIL='t@t', GIT_COMMITTER_NAME='t', GIT_COMMITTER_EMAIL='t@t'))
        g('init', '-q', '-b', 'master')
        open(os.path.join(t, 'work/.gitignore'), 'w').write('native/build/\n')
        g('add', '-A')
        g('commit', '-q', '-m', 'x')
        g('init', '-q', '--bare', os.path.join(t, 'remote.git'), cwd=t)
        g('remote', 'add', 'origin', os.path.join(t, 'remote.git'))
        g('push', '-q', 'origin', 'master')
        self.sha = g('rev-parse', 'HEAD').stdout.strip()
        self.code = '10050'
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
        self.assertIn('no fast or full lane run with the Android unit tests', out)

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
