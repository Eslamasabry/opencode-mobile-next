#!/usr/bin/env python3
"""Release smoke: four checks on ONE connected Android device or emulator, and a small result file.

    native/tool/qa/release_smoke.sh --new-apk <build.apk> [--previous-apk <old.apk>] [--out DIR] [--commit SHA]

The device is chosen by the environment variable OC_SMOKE_SERIAL (an adb serial). It is never written to a file.
Tools: adb (OC_ADB overrides), python3. Plain adb and uiautomator only; the labels it taps and looks for are read from
this repository's own words files (ui/words), so a renamed button changes the script's target with the app.

Checks, in order (a later check still runs after an earlier one fails, so one run reports everything):
  a  install: the new APK goes over the installed build (adb install -r, never uninstall) and the app opens with its
     conversations still there. What the person owns is whatever the Conversations screen shows before the install
     that is not one of the app's own words; every such text must still be there after it.
  b  server: Settings > This phone says the phone's own server is running (it is started with the Start button when off).
  c  reply: a new conversation, a prompt that asks for one exact word, and that word comes back as a reply.
  d  permission: a prompt that makes the agent write a file; the permission question appears, "Allow once" answers it,
     the question goes away and the run goes on.

Result file: <out>/smoke-<commit>.json with the commit, the installed version code, the device model, the time, the
APK's sha256 and each check's pass or fail ("pass": true only when all four passed). scripts/native-release.sh refuses
to go on without such a file for its HEAD commit.
Exit: 0 all four passed, 1 a check failed, 2 could not run (no device, no APK, no adb).
"""
import argparse, datetime, glob, hashlib, json, os, re, secrets, subprocess, sys, time
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
NATIVE = os.path.abspath(os.path.join(HERE, '..', '..'))
REPO = os.path.abspath(os.path.join(NATIVE, '..'))
WORDS = os.path.join(NATIVE, 'ui', 'words', 'src', 'commonMain', 'composeResources', 'values')
PKG = 'io.github.eslamasabry.opencode_mobile'
ADB = os.environ.get('OC_ADB', 'adb')
SERIAL = os.environ.get('OC_SMOKE_SERIAL', '').strip()
DUMP = '/sdcard/oc_release_smoke_ui.xml'


def die(msg, code=2):
    print(f'release smoke: {msg}', file=sys.stderr)
    sys.exit(code)


def adb(*args, check=False, timeout=60):
    p = subprocess.run([ADB, '-s', SERIAL, *args], capture_output=True, text=True, timeout=timeout)
    if check and p.returncode != 0:
        die(f'adb {" ".join(args[:2])} failed: {(p.stdout + p.stderr).strip()[:300]}')
    return p


def shell(*args, timeout=60):
    return adb('shell', *args, timeout=timeout).stdout


# ---- the app's own words ------------------------------------------------------------------------------------------------
_words = {}
for _f in glob.glob(os.path.join(WORDS, '*.xml')):
    for _e in ET.parse(_f).getroot().iter('string'):
        _words[_e.get('name')] = ''.join(_e.itertext()).replace("\\'", "'").replace('\\n', '\n')


def word(key):
    if key not in _words:
        die(f'the words files have no "{key}" (the script is out of date with the app)')
    return _words[key]


CHROME = {v.strip() for v in _words.values() if '%' not in v and '{' not in v}


# ---- uiautomator --------------------------------------------------------------------------------------------------------
class Node:
    def __init__(self, el):
        a = el.attrib
        self.text, self.desc, self.cls = a.get('text', ''), a.get('content-desc', ''), a.get('class', '')
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', a.get('bounds', ''))
        self.box = tuple(int(x) for x in m.groups()) if m else (0, 0, 0, 0)

    @property
    def center(self):
        x1, y1, x2, y2 = self.box
        return (x1 + x2) // 2, (y1 + y2) // 2

    def label(self):
        return self.text or self.desc


def screen():
    """-> list of Node for what is on screen now (empty when the dump failed)."""
    for _ in range(3):
        adb('shell', 'uiautomator', 'dump', DUMP)
        p = subprocess.run([ADB, '-s', SERIAL, 'exec-out', 'cat', DUMP], capture_output=True, timeout=60)
        try:
            root = ET.fromstring(p.stdout.decode('utf-8', 'replace'))
            return [Node(e) for e in root.iter('node')]
        except ET.ParseError:
            time.sleep(1)
    return []


def find(nodes, text, exact=True):
    t = text.strip()
    return [n for n in nodes if n.label().strip() and (n.label().strip() == t if exact else t in n.label())]


def tap(node):
    x, y = node.center
    adb('shell', 'input', 'tap', str(x), str(y))


def wait_for(what, timeout, test):
    """Polls the screen every 2 s until test(nodes) is truthy; -> (value, nodes). value is None on timeout."""
    end = time.time() + timeout
    nodes = []
    while time.time() < end:
        nodes = screen()
        v = test(nodes)
        if v:
            return v, nodes
        time.sleep(2)
    return None, nodes


def swipe_up():
    w, h = screen_size()
    adb('shell', 'input', 'swipe', str(w // 2), str(int(h * 0.75)), str(w // 2), str(int(h * 0.3)), '350')


def screen_size():
    m = re.search(r'(\d+)x(\d+)', shell('wm', 'size'))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def type_text(s):
    adb('shell', 'input', 'text', s.replace(' ', '%s'))


class Fail(Exception):
    pass


def launch():
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    shell('am', 'force-stop', PKG)
    time.sleep(1)
    shell('monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1')


def conversations_screen(timeout=90):
    v, nodes = wait_for('Conversations', timeout, lambda ns: find(ns, word('chatsHomeTitle')))
    if not v:
        raise Fail(f'the app did not show "{word("chatsHomeTitle")}" within {timeout} s (is the phone unlocked and the app past its setup?)')
    return nodes


def user_texts(nodes):
    """Texts on the Conversations screen that are the person's own (not one of the app's words, no digits like times)."""
    out = set()
    for n in nodes:
        t = n.label().strip()
        if len(t) >= 3 and t not in CHROME and not re.search(r'\d', t) and not n.cls.endswith('EditText'):
            out.add(t)
    return out


def installed_code():
    m = re.search(r'versionCode=(\d+)', shell('dumpsys', 'package', PKG))
    return int(m.group(1)) if m else None


def apk_code(path):
    for tool in sorted(glob.glob(os.path.expanduser('~/Android/Sdk/build-tools/*/aapt2')) + glob.glob(os.environ.get('ANDROID_HOME', '/nonexistent') + '/build-tools/*/aapt2'), reverse=True) + ['aapt2']:
        try:
            out = subprocess.run([tool, 'dump', 'badging', path], capture_output=True, text=True, timeout=60).stdout
        except OSError:
            continue
        m = re.search(r"versionCode='(\d+)'", out)
        if m:
            return int(m.group(1))
    return None


# ---- the four checks ----------------------------------------------------------------------------------------------------
def check_a(new_apk, previous_apk, ctx):
    have = installed_code()
    new_code = apk_code(new_apk)
    if have is None:
        if not previous_apk:
            raise Fail('the previous release is not installed on this device and no --previous-apk was given (there is nothing to update over)')
        adb('install', '-r', previous_apk, timeout=300, check=True)
        have = installed_code()
        print(f'  a  installed the previous build ({have}) first; create a conversation in it and run again')
        raise Fail('the previous build was only just installed, so it holds no conversations yet')
    if new_code is not None and have >= new_code:
        raise Fail(f'the installed build ({have}) is not older than the new one ({new_code}); an update over it is not a test of updating')
    launch()
    before = user_texts(conversations_screen())
    if not before:
        raise Fail('the previous build shows no conversations to keep; open one with a message in it first, then run again')
    p = adb('install', '-r', new_apk, timeout=600)
    if p.returncode != 0 or 'Success' not in p.stdout:
        raise Fail('the install over the previous build was refused: ' + (p.stdout + p.stderr).strip().splitlines()[-1][:160])
    ctx['version_code'] = installed_code()
    launch()
    after = user_texts(conversations_screen())
    lost = sorted(before - after)
    if lost:
        raise Fail(f'{len(lost)} of {len(before)} conversation texts were gone after the update, for instance "{lost[0][:60]}"')
    return f'updated {have} -> {ctx["version_code"]}; {len(before)} of the person\'s own texts still there'


def open_this_phone():
    nodes = conversations_screen(30)
    s = find(nodes, word('librarySettingsTitle'))
    if not s:
        raise Fail(f'no "{word("librarySettingsTitle")}" entry on the Conversations screen')
    tap(s[0])
    v, nodes = wait_for('Settings', 20, lambda ns: find(ns, word('settingsHubSearch'), exact=False))
    if not v:
        raise Fail('Settings did not open')
    for _ in range(8):
        hit = find(nodes, word('settingsV3ThisPhone'))
        if hit:
            tap(hit[0])
            return
        swipe_up()
        nodes = screen()
    raise Fail(f'no "{word("settingsV3ThisPhone")}" row in Settings')


def check_b(ctx):
    launch()
    conversations_screen()
    open_this_phone()
    running = word('thisPhoneAnswerRunning')
    v, nodes = wait_for('server state', 20, lambda ns: find(ns, running) or find(ns, word('thisPhoneStart')) or find(ns, word('thisPhoneAnswerOff')))
    if not v:
        raise Fail('the "This phone" page showed neither a running nor a stopped server')
    if not find(nodes, running):
        start = find(nodes, word('thisPhoneStart'))
        if not start:
            raise Fail(f'the server is not running and there is no "{word("thisPhoneStart")}" button')
        tap(start[0])
        v, nodes = wait_for('server running', 120, lambda ns: find(ns, running))
        if not v:
            raise Fail(f'the phone\'s own server did not say "{running}" within 120 s of Start')
        return 'started from "Start the server" and running'
    return 'already running'


def new_conversation():
    launch()
    nodes = conversations_screen()
    nc = find(nodes, word('chatsHomeNewChat')) or find(nodes, word('chatsHomeNewShort'))
    if not nc:
        raise Fail(f'no "{word("chatsHomeNewChat")}" button')
    tap(nc[0])

    def composer(ns):
        return [n for n in ns if n.cls.endswith('EditText') or n.label().strip() in (word('kitComposerField'), word('chatUiAskOpenCode'))]
    v, nodes = wait_for('composer', 15, composer)
    if not v:
        project = os.environ.get('OC_SMOKE_PROJECT', '').strip()
        hit = find(nodes, project) if project else []
        if not hit:
            raise Fail('a project has to be picked and OC_SMOKE_PROJECT does not name one on screen (set it to a project of the phone\'s own server)')
        tap(hit[0])
        v, nodes = wait_for('composer', 30, composer)
        if not v:
            raise Fail('the conversation did not open with a message box')
    return composer


def send(composer, text):
    nodes = screen()
    box = composer(nodes)
    if not box:
        raise Fail('no message box to type in')
    tap(box[0])
    time.sleep(1)
    type_text(text)
    time.sleep(1)
    nodes = screen()
    send_btn = find(nodes, word('kitComposerSend'))
    if not send_btn:
        raise Fail(f'no "{word("kitComposerSend")}" button after typing')
    tap(send_btn[0])


def check_c(ctx):
    composer = new_conversation()
    ctx['composer'] = composer
    tok = 'OK-' + secrets.token_hex(3)
    send(composer, f'Reply with exactly this word and nothing else: {tok}')
    v, _ = wait_for('reply', 150, lambda ns: [n for n in ns if n.label().strip() == tok])
    if not v:
        raise Fail(f'no reply that is exactly "{tok}" within 150 s (does the phone\'s server have a working model?)')
    return 'the reply came back'


def check_d(ctx):
    composer = ctx.get('composer') or new_conversation()
    name = 'smoke-' + secrets.token_hex(3) + '.txt'
    send(composer, f'Create a file named {name} in the project with the word hello in it. Do it now.')
    allow = word('kitRequestAllowOnce')
    v, nodes = wait_for('permission question', 120, lambda ns: find(ns, allow))
    if not v:
        raise Fail(f'no permission question ("{allow}") within 120 s (is the permission mode "ask" and the model able to write files?)')
    tap(v[0])
    gone, nodes = wait_for('question gone', 60, lambda ns: not find(ns, allow))
    if not gone:
        raise Fail('the permission question stayed after "Allow once"')
    mentions = lambda ns: [n for n in ns if name in n.label()]
    v, nodes = wait_for('run goes on', 120, lambda ns: len(mentions(ns)) >= 2)
    if not v:
        raise Fail(f'after "Allow once" nothing new about {name} appeared within 120 s (the run did not go on)')
    return 'the question appeared, Allow once answered it, the run went on'


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--new-apk', required=True)
    ap.add_argument('--previous-apk', default='')
    ap.add_argument('--out', default=os.path.join(NATIVE, 'build', 'release-smoke'))
    ap.add_argument('--commit', default='')
    a = ap.parse_args()
    if not SERIAL:
        die('set OC_SMOKE_SERIAL to the adb serial of the device to test (run `adb devices`); it is never written to a file')
    if not os.path.isfile(a.new_apk):
        die(f'no APK at {a.new_apk}')
    if a.previous_apk and not os.path.isfile(a.previous_apk):
        die(f'no APK at {a.previous_apk}')
    commit = a.commit or subprocess.run(['git', '-C', REPO, 'rev-parse', 'HEAD'], capture_output=True, text=True).stdout.strip()
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        die('could not read the commit (pass --commit <40 hex>)')
    if adb('get-state').stdout.strip() != 'device':
        die('the device in OC_SMOKE_SERIAL is not online (adb get-state)')
    model = shell('getprop', 'ro.product.model').strip() or 'unknown'
    print(f'release smoke: commit {commit[:10]}, device model {model}')
    ctx = {}
    results = {}
    for key, name, fn in (('a', 'update over the previous build keeps the conversations', lambda: check_a(a.new_apk, a.previous_apk, ctx)),
                          ('b', "the phone's own server starts", lambda: check_b(ctx)),
                          ('c', 'a prompt gets a reply', lambda: check_c(ctx)),
                          ('d', 'a permission question is answered and the run goes on', lambda: check_d(ctx))):
        try:
            note = fn()
            results[key] = {'name': name, 'pass': True, 'note': note}
            print(f'  PASS {key}  {name}: {note}')
        except Fail as e:
            results[key] = {'name': name, 'pass': False, 'note': str(e)}
            print(f'  FAIL {key}  {name}: {e}')
        except Exception as e:  # a tool error is a failed check, never a pass
            results[key] = {'name': name, 'pass': False, 'note': f'{type(e).__name__}: {str(e)[:160]}'}
            print(f'  FAIL {key}  {name}: {results[key]["note"]}')
    sha = hashlib.sha256(open(a.new_apk, 'rb').read()).hexdigest()
    ok = all(r['pass'] for r in results.values())
    out = {'commit': commit, 'version_code': ctx.get('version_code') or installed_code(), 'device_model': model,
           'apk_sha256': sha, 'time_utc': datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
           'checks': results, 'pass': ok}
    os.makedirs(a.out, exist_ok=True)
    path = os.path.join(a.out, f'smoke-{commit}.json')
    json.dump(out, open(path, 'w'), indent=2)
    print(f'release smoke: {"PASS" if ok else "FAIL"}; result file {path}')
    sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
