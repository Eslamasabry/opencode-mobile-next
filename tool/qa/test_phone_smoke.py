"""Offline tests for tool/qa/phone_smoke.sh: fake adb and apksigner, no phone."""
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import textwrap
import unittest
from xml.sax.saxutils import quoteattr

from tool.qa import phone_smoke
from tool.qa.phone_smoke import PACKAGE

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'tool/qa/phone_smoke.sh'
APK_DIGEST = 'aa' * 32
OTHER_DIGEST = 'bb' * 32

FAKE_ADB = textwrap.dedent('''\
    #!/usr/bin/env python3
    import json, os, sys
    scenario = json.load(open(os.environ['FAKE_SCENARIO']))
    args = sys.argv[1:]
    with open(os.environ['FAKE_LOG'], 'a') as log:
        log.write(json.dumps(args) + '\\n')
    args = args[2:] if args[:1] == ['-s'] else args
    joined = ' '.join(args)
    if args == ['get-state']:
        print('device')
    elif joined == 'shell getprop ro.product.cpu.abi':
        print(scenario.get('abi', 'arm64-v8a'))
    elif joined.startswith('shell pm path'):
        if scenario.get('installed', True):
            print('package:/data/app/~~x/' + scenario['package'] + '-y/base.apk')
    elif args[:1] == ['pull']:
        open(args[2], 'w').write('installed')
    elif args[:2] == ['install', '-r']:
        print('Performing Streamed Install')
        print('Success')
    elif joined == 'shell dumpsys window':
        print('  mCurrentFocus=Window{1a2b u0 ' + scenario['focus'] + '}')
    elif args[:3] == ['exec-out', 'uiautomator', 'dump']:
        print(scenario.get('dump', ''))
''')

FAKE_APKSIGNER = textwrap.dedent('''\
    #!/usr/bin/env python3
    import json, os, sys
    scenario = json.load(open(os.environ['FAKE_SCENARIO']))
    path = sys.argv[-1]
    content = open(path).read()
    digest = scenario['installed_digest'] if content.startswith('installed') else scenario['apk_digest']
    print('Signer #1 certificate DN: CN=Test')
    print('Signer #1 certificate SHA-256 digest: ' + digest)
''')


def write_exe(path, body):
    path.write_text(body)
    path.chmod(path.stat().st_mode | stat.S_IXUSR)


class ScriptRefusalTest(unittest.TestCase):
    """Runs the real shell entry point with fake adb/apksigner on PATH."""

    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        bin_dir = self.dir / 'bin'
        bin_dir.mkdir()
        write_exe(bin_dir / 'adb', FAKE_ADB)
        write_exe(bin_dir / 'apksigner', FAKE_APKSIGNER)
        self.apk = self.dir / 'app-release.apk'
        self.apk.write_text('new apk')
        self.log = self.dir / 'adb.log'
        self.log.write_text('')
        self.env = dict(os.environ, PATH=f'{bin_dir}{os.pathsep}{os.environ["PATH"]}',
                        FAKE_LOG=str(self.log), FAKE_SCENARIO=str(self.dir / 'scenario.json'),
                        TMPDIR=str(self.dir))

    def run_smoke(self, **scenario):
        base = dict(package=PACKAGE, focus=f'{PACKAGE}/{PACKAGE}.MainActivity',
                    installed_digest=APK_DIGEST, apk_digest=APK_DIGEST)
        base.update(scenario)
        Path(self.env['FAKE_SCENARIO']).write_text(json.dumps(base))
        done = subprocess.run(['bash', str(SCRIPT), 'R58N12345', str(self.apk)],
                              capture_output=True, text=True, env=self.env, timeout=60)
        calls = [json.loads(line) for line in self.log.read_text().splitlines()]
        return done, calls

    def assertNothingDestructive(self, calls):
        flat = [' '.join(c) for c in calls]
        for word in ('uninstall', 'clear', 'screencap', 'screenrecord'):
            self.assertFalse(any(word in c for c in flat), f'{word} was sent: {flat}')

    def test_wrong_signer_refuses_before_install(self):
        done, calls = self.run_smoke(installed_digest=OTHER_DIGEST)
        self.assertEqual(done.returncode, 2, done.stdout + done.stderr)
        self.assertIn('REFUSED: signer mismatch', done.stdout)
        self.assertIn('BBBBBBBB', done.stdout)
        self.assertFalse(any('install' in c for c in calls), calls)
        self.assertFalse(any('input' in ' '.join(c) for c in calls))
        self.assertNothingDestructive(calls)

    def test_not_installed_refuses_instead_of_fresh_install(self):
        done, calls = self.run_smoke(installed=False)
        self.assertEqual(done.returncode, 2)
        self.assertIn('REFUSED: the app is not installed', done.stdout)
        self.assertFalse(any('install' in c for c in calls))
        self.assertNothingDestructive(calls)

    def test_non_arm_phone_refused(self):
        done, calls = self.run_smoke(abi='x86_64')
        self.assertEqual(done.returncode, 2)
        self.assertIn('REFUSED: not an ARM phone', done.stdout)
        self.assertFalse(any('pull' in c for c in calls))

    def test_same_signer_installs_with_replace_only(self):
        dump = (f'<hierarchy><node package="{PACKAGE}" bounds="[0,0][1080,2400]">'
                f'<node text="Settings" package="{PACKAGE}" bounds="[0,0][1080,90]"/>'
                '</node></hierarchy>')
        done, calls = self.run_smoke(dump=dump,
                                     focus='com.android.incallui/com.android.incallui.InCallActivity')
        self.assertEqual(done.returncode, 3, done.stdout)
        self.assertFalse(any('input' in ' '.join(c) for c in calls), calls)
        installs = [c[2:] for c in calls if 'install' in c]
        self.assertEqual(installs, [['install', '-r', str(self.apk)]])
        self.assertIn('PASS install_same_signer', done.stdout)
        self.assertNothingDestructive(calls)


class FakePhone:
    """In-process adb runner with a tiny model of the app's screens."""

    def __init__(self, focus=None, replies=True, abi='arm64-v8a'):
        self.focus = focus or f'{PACKAGE}/{PACKAGE}.MainActivity'
        self.abi, self.replies = abi, replies
        self.screen, self.calls, self.messages, self.draft = 'home', [], [], ''
        self.chat_deleted = False
        self.dir = Path(tempfile.mkdtemp())
        self.apk = self.dir / 'app.apk'
        self.apk.write_text('apk')

    def labels(self):
        rows = {
            'home': [('New conversation', 'View'), ('Settings', 'View')]
                    + ([] if self.chat_deleted or not self.messages
                       else [(self.messages[0], 'View')]),
            'settings': [('Servers', 'View')],
            'servers': [('Other server\nNot connected', 'View'),
                        ('On this phone\nConnected', 'View')],
            'pick': [('armsmoke', 'View')],
            'chat': [('More options', 'Button')] + [(m, 'View') for m in self.messages]
                    + [('', 'android.widget.EditText'), ('Send', 'Button')],
            'menu': [('Archive conversation', 'View'), ('Delete conversation', 'View')],
            'confirm': [('Cancel', 'Button'), ('Delete', 'Button')],
        }
        return rows[self.screen]

    def dump(self):
        nodes = []
        for i, (label, cls) in enumerate(self.labels()):
            desc = quoteattr(label, {'\n': '&#10;'})
            nodes.append(f'<node text="" content-desc={desc} class="{cls}" package="{PACKAGE}" '
                         f'bounds="[0,{i * 100}][1080,{i * 100 + 90}]" />')
        return ('<?xml version="1.0"?><hierarchy rotation="0">'
                f'<node text="" class="FrameLayout" package="{PACKAGE}" bounds="[0,0][1080,2400]">'
                + ''.join(nodes) + '</node></hierarchy>')

    def tap(self, y):
        label = self.labels()[y // 100][0]
        moves = {('home', 'Settings'): 'settings', ('settings', 'Servers'): 'servers',
                 ('home', 'New conversation'): 'pick', ('pick', 'armsmoke'): 'chat',
                 ('chat', 'More options'): 'menu', ('menu', 'Delete conversation'): 'confirm'}
        if (self.screen, label) in moves:
            self.screen = moves[(self.screen, label)]
        elif self.screen == 'confirm' and label == 'Delete':
            self.chat_deleted, self.screen = True, 'home'
        elif self.screen == 'chat' and label == 'Send':
            self.send()

    def send(self):
        prompt, self.draft = self.draft, ''
        self.messages.append(prompt)
        if not self.replies:
            return
        if 'word ok' in prompt:
            self.messages.append('ok')
        elif 'git --version' in prompt:
            self.messages.extend(['git version 2.47.1', 'aarch64'])
        elif 'tar -czf' in prompt:
            self.messages.append(re.search(r'echo (\S+) \|', prompt).group(1).upper())
        elif 'rm -rf' in prompt:
            self.messages.append('REMOVED')

    def __call__(self, cmd, capture_output, timeout):
        if cmd[0].endswith('apksigner'):
            return subprocess.CompletedProcess(cmd, 0, f'Signer #1 certificate SHA-256 digest: {APK_DIGEST}\n'.encode(), b'')
        args = cmd[3:]
        self.calls.append(args)
        joined = ' '.join(args)
        out = ''
        if args == ['get-state']:
            out = 'device'
        elif joined == 'shell getprop ro.product.cpu.abi':
            out = self.abi
        elif joined.startswith('shell pm path'):
            out = f'package:/data/app/{PACKAGE}/base.apk'
        elif args[0] == 'install':
            out = 'Success'
        elif joined == 'shell dumpsys window':
            out = f'mCurrentFocus=Window{{9 u0 {self.focus}}}'
        elif args[:3] == ['exec-out', 'uiautomator', 'dump']:
            out = self.dump()
        elif args[:3] == ['shell', 'input', 'tap']:
            self.tap(int(args[4]))
        elif args[:2] == ['shell', 'am']:
            self.screen = 'home'
        elif args[0] == 'shell' and args[1].startswith('input text '):
            self.draft = args[1][len("input text '"):-1].replace('%s', ' ')
        return subprocess.CompletedProcess(cmd, 0, out.encode(), b'')


class Clock:
    def __init__(self):
        self.now = 0.0

    def sleep(self, seconds):
        self.now += seconds

    def __call__(self):
        return self.now


class SmokeFlowTest(unittest.TestCase):
    def run_main(self, phone):
        lines, clock = [], Clock()
        os.environ['TMPDIR'] = str(phone.dir)
        code = phone_smoke.main(['R58N12345', str(phone.apk), '--reply-timeout', '30'],
                                runner=phone, sleep=clock.sleep, clock=clock, out=lines.append)
        return code, lines

    def test_full_pass_cleans_up(self):
        phone = FakePhone()
        code, lines = self.run_main(phone)
        self.assertEqual(code, 0, lines)
        for name in ('install_same_signer', 'phone_connected', 'new_chat_reply_ok',
                     'shell_git_uname_arm', 'tar_roundtrip_tmp', 'cleanup'):
            self.assertTrue(any(l.startswith(f'PASS {name}') for l in lines), (name, lines))
        self.assertIn('aarch64', '\n'.join(lines))
        self.assertEqual(lines[-1], 'RESULT: PASS')
        self.assertTrue(phone.chat_deleted)
        self.assertTrue(any('rm -rf /tmp/armsmoke' in m for m in phone.messages))

    def test_incoming_call_blocks_every_tap(self):
        phone = FakePhone(focus='com.android.incallui/com.android.incallui.InCallActivity')
        code, lines = self.run_main(phone)
        self.assertEqual(code, phone_smoke.EXIT_FOREGROUND, lines)
        self.assertIn('REFUSED: foreground is com.android.incallui', '\n'.join(lines))
        self.assertFalse(any(c[:2] == ['shell', 'input'] for c in phone.calls), phone.calls)
        self.assertFalse(any(c[0] == 'shell' and c[1].startswith('input') for c in phone.calls))

    def test_notification_shade_counts_as_not_foreground(self):
        phone = FakePhone(focus='NotificationShade')
        code, lines = self.run_main(phone)
        self.assertEqual(code, phone_smoke.EXIT_FOREGROUND)
        self.assertIn('foreground is NotificationShade', '\n'.join(lines))

    def test_missing_reply_fails_and_still_cleans_up(self):
        phone = FakePhone(replies=False)
        code, lines = self.run_main(phone)
        self.assertEqual(code, phone_smoke.EXIT_FAIL)
        self.assertTrue(any(l.startswith('FAIL new_chat_reply_ok') for l in lines), lines)
        self.assertTrue(phone.chat_deleted, 'the scratch conversation must still be deleted')
        self.assertEqual(lines[-1], 'RESULT: FAIL')


class GuardTest(unittest.TestCase):
    def test_destructive_commands_refused(self):
        for args in (('uninstall', PACKAGE), ('shell', 'pm', 'clear', PACKAGE),
                     ('shell', f'pm clear {PACKAGE}'), ('shell', 'cmd', 'package', 'clear', PACKAGE),
                     ('exec-out', 'screencap', '-p'), ('shell', 'screenrecord', '/sdcard/a.mp4'),
                     ('shell', f'true; pm uninstall {PACKAGE}')):
            with self.assertRaises(phone_smoke.Refused, msg=args):
                phone_smoke.guard_command(args)

    def test_prompt_with_quote_is_not_typed(self):
        ui = phone_smoke.Ui(adb=None)
        with self.assertRaises(phone_smoke.CheckFailed):
            ui.type_into_composer("it's")


if __name__ == '__main__':
    unittest.main()
