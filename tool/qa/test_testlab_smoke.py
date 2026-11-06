"""Offline tests for tool/qa/testlab_smoke.sh with a fake gcloud; nothing leaves the machine."""
import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'tool/qa/testlab_smoke.sh'
PROJECT = 'oc-mobile-smoke'

FAKE_GCLOUD = textwrap.dedent('''\
    #!/usr/bin/env python3
    import json, os, sys
    s = json.load(open(os.environ['FAKE_SCENARIO']))
    args = sys.argv[1:]
    with open(os.environ['FAKE_LOG'], 'a') as log:
        log.write(json.dumps(args) + '\\n')
    def reply(key):
        out, rc = s.get(key, ['', 0])
        sys.stdout.write(out)
        sys.exit(rc)
    if args[:2] == ['auth', 'list']:
        reply('auth')
    if args[:3] == ['billing', 'projects', 'describe']:
        reply('billing')
    if args[:5] == ['firebase', 'test', 'android', 'models', 'list']:
        reply('models')
    if args[:4] == ['firebase', 'test', 'android', 'run']:
        reply('run')
    sys.exit(99)
''')

CATALOG = ('oriole\t30;31;32;33\n'
           'a15\t34;35\n'
           'redfin\t30\n'
           'e3q\t34\n'
           'bad model\t36\n')


class TestlabSmokeTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        gcloud = self.dir / 'gcloud'
        gcloud.write_text(FAKE_GCLOUD)
        gcloud.chmod(gcloud.stat().st_mode | stat.S_IXUSR)
        self.apk = self.dir / 'app-release.apk'
        self.apk.write_text('apk')
        self.log = self.dir / 'gcloud.log'
        self.log.write_text('')
        self.ledger = self.dir / 'ledger'
        self.env = dict(os.environ, GCLOUD=str(gcloud), FAKE_LOG=str(self.log),
                        FAKE_SCENARIO=str(self.dir / 'scenario.json'),
                        TESTLAB_LEDGER_DIR=str(self.ledger), TMPDIR=str(self.dir))
        self.env.pop('TESTLAB_DEVICES', None)
        self.env.pop('TESTLAB_DRY_RUN', None)

    def run_script(self, **scenario):
        base = {'auth': ['owner@example.com\n', 0],
                'billing': [f'{PROJECT}||\n', 0],
                'models': [CATALOG, 0],
                'run': ['| OUTCOME | TEST_AXIS_VALUE |\n| Passed | a15-35-en-portrait |\n', 0]}
        base.update(scenario)
        Path(self.env['FAKE_SCENARIO']).write_text(json.dumps(base))
        done = subprocess.run(['bash', str(SCRIPT), PROJECT, str(self.apk)], capture_output=True,
                              text=True, env=self.env, timeout=60)
        calls = [json.loads(line) for line in self.log.read_text().splitlines()]
        return done, calls

    def ran_tests(self, calls):
        return [c for c in calls if c[:4] == ['firebase', 'test', 'android', 'run']]

    def last_line(self, done):
        return done.stdout.strip().splitlines()[-1]

    def test_billing_account_attached_refuses(self):
        done, calls = self.run_script(billing=[f'{PROJECT}|True|billingAccounts/0X0X0X-0X0X0X-0X0X0X\n', 0])
        self.assertEqual(done.returncode, 2, done.stdout)
        self.assertIn('REFUSED', self.last_line(done))
        self.assertIn('billing account attached', done.stdout)
        self.assertEqual(self.ran_tests(calls), [])

    def test_billing_account_without_enabled_flag_still_refuses(self):
        done, calls = self.run_script(billing=[f'{PROJECT}||billingAccounts/0X0X0X\n', 0])
        self.assertEqual(done.returncode, 2)
        self.assertEqual(self.ran_tests(calls), [])

    def test_billing_enabled_true_refuses(self):
        done, calls = self.run_script(billing=[f'{PROJECT}|True|\n', 0])
        self.assertEqual(done.returncode, 2)
        self.assertEqual(self.ran_tests(calls), [])

    def test_unreadable_billing_fails_closed(self):
        for billing in (['', 1], ['', 0], ['other-project||\n', 0], ['garbage\n', 0]):
            with self.subTest(billing=billing):
                done, calls = self.run_script(billing=billing)
                self.assertEqual(done.returncode, 2, done.stdout)
                self.assertIn('REFUSED', self.last_line(done))
                self.assertEqual(self.ran_tests(calls), [])

    def test_not_signed_in_refuses(self):
        done, calls = self.run_script(auth=['', 0])
        self.assertEqual(done.returncode, 2)
        self.assertFalse(any(c[:1] == ['billing'] for c in calls))

    def test_spark_runs_robo_on_three_newest_arm_devices(self):
        done, calls = self.run_script()
        self.assertEqual(done.returncode, 0, done.stdout + done.stderr)
        self.assertTrue(self.last_line(done).startswith('TESTLAB: PASS'))
        run = self.ran_tests(calls)
        self.assertEqual(len(run), 1)
        devices = [run[0][i + 1] for i, a in enumerate(run[0]) if a == '--device']
        self.assertEqual(devices, ['model=a15,version=35', 'model=e3q,version=34',
                                   'model=oriole,version=33'])
        self.assertIn('robo', run[0])
        models = next(c for c in calls if c[:5] == ['firebase', 'test', 'android', 'models', 'list'])
        self.assertIn('--filter=form=PHYSICAL AND supportedAbis:arm64-v8a', models)

    def test_quota_error_is_not_run_and_exit_zero(self):
        done, _ = self.run_script(run=['ERROR: (gcloud.firebase.test.android.run) Quota exceeded '
                                       'for quota metric physical device tests per day\n', 1])
        self.assertEqual(done.returncode, 0)
        self.assertTrue(self.last_line(done).startswith('NOT RUN: free quota used'))

    def test_local_ledger_spent_skips_without_uploading(self):
        self.run_script()  # 3 of 5 used
        done, calls = self.run_script()
        self.assertEqual(done.returncode, 0)
        self.assertTrue(self.last_line(done).startswith('NOT RUN: free quota used'))
        self.assertEqual(len(self.ran_tests(calls)), 1, 'only the first run may upload')

    def test_device_failure_fails(self):
        done, _ = self.run_script(run=['| Failed | oriole-33 |\n', 10])
        self.assertEqual(done.returncode, 1)
        self.assertTrue(self.last_line(done).startswith('TESTLAB: FAIL'))

    def test_too_few_arm_devices_refuses(self):
        done, calls = self.run_script(models=['oriole\t33\n', 0])
        self.assertEqual(done.returncode, 2)
        self.assertEqual(self.ran_tests(calls), [])

    def test_explicit_devices_and_dry_run(self):
        self.env['TESTLAB_DEVICES'] = 'model=a,version=34 model=b,version=33 model=c,version=35'
        self.env['TESTLAB_DRY_RUN'] = '1'
        done, calls = self.run_script()
        self.assertEqual(done.returncode, 0)
        self.assertIn('DRY RUN:', done.stdout)
        self.assertEqual(self.ran_tests(calls), [])
        self.assertFalse(any(c[:5] == ['firebase', 'test', 'android', 'models', 'list'] for c in calls))


if __name__ == '__main__':
    unittest.main()
