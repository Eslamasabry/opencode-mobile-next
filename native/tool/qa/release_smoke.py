#!/usr/bin/env python3
"""Release smoke: four checks on ONE connected Android device or emulator, and a small result file.

    native/tool/qa/release_smoke.sh --new-apk <build.apk> [--previous-apk <old.apk>] [--out DIR] [--commit SHA]
    optional env: OC_SMOKE_SERVER (shown name of a saved connection for checks c and d), OC_SMOKE_PROJECT

The device is chosen by the environment variable OC_SMOKE_SERIAL (an adb serial). It is never written to a file.
Tools: adb (OC_ADB overrides; else adb on PATH, else $ANDROID_HOME or ~/Android/Sdk platform-tools), python3.
--commit takes a full or short commit of this repository (default HEAD). Before a run: the device holds an OLDER build
of the app with at least one conversation (adb install cannot go down), and New conversation's agent and model are the
cheap ones you want the two prompts to use (the script sends with whatever New conversation shows). Plain adb and uiautomator only; the labels it taps and looks for are read from
this repository's own words files (ui/words), so a renamed button changes the script's target with the app.

Checks, in order (a later check still runs after an earlier one fails, so one run reports everything):
  a  install: the new APK goes over the installed build (adb install -r, never uninstall) and the app opens with its
     conversations still there. What the person owns is whatever the Conversations screen shows before the install
     that is not one of the app's own words; every such text must still be there after it.
  b  server: Settings > Server > This phone says the phone's own server is running (it is started with the Start button when off).
  c  reply: New conversation, a prompt that asks for one exact word, Start, and that word comes back as a reply.
  d  permission: a fresh New conversation and a prompt that makes the agent write one file; every permission question
     that follows is answered with "Allow once" (at most six: an agent may also ask to read or list), the questions
     go away and the run goes on.

Result file: <out>/smoke-<commit>.json with the commit, the installed version code, the device model, the time, the
APK's sha256 and each check's pass or fail ("pass": true only when all four passed). scripts/native-release.sh refuses
to go on without such a file for its HEAD commit.
Exit: 0 all four passed, 1 a check failed, 2 could not run (no device, no APK, no adb).
"""
import argparse, datetime, glob, hashlib, json, os, re, secrets, shutil, subprocess, sys, time
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
NATIVE = os.path.abspath(os.path.join(HERE, '..', '..'))
REPO = os.path.abspath(os.path.join(NATIVE, '..'))
WORDS = os.path.join(NATIVE, 'ui', 'words', 'src', 'commonMain', 'composeResources', 'values')
PKG = 'io.github.eslamasabry.opencode_mobile'
def _find_adb():
    if os.environ.get('OC_ADB', '').strip():
        return os.environ['OC_ADB'].strip()
    for c in (shutil.which('adb'), os.path.join(os.environ.get('ANDROID_HOME', '/nonexistent'), 'platform-tools', 'adb'),
              os.path.expanduser('~/Android/Sdk/platform-tools/adb')):
        if c and os.path.isfile(c) and os.access(c, os.X_OK):
            return c
    return 'adb'


ADB = _find_adb()
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
# The shared words, and the Android app's own (notices such as "The app was closed in the background" live there).
for _f in glob.glob(os.path.join(WORDS, '*.xml')) + glob.glob(os.path.join(NATIVE, 'androidApp', 'src', 'main', 'res', 'values', 'strings*.xml')):
    for _e in ET.parse(_f).getroot().iter('string'):
        _words[_e.get('name')] = ''.join(_e.itertext()).replace("\\'", "'").replace('\\u0027', "'").replace('\\n', '\n')


def word(key):
    if key not in _words:
        die(f'the words files have no "{key}" (the script is out of date with the app)')
    return _words[key]


CHROME = {v.strip() for v in _words.values() if '%' not in v and '{' not in v}
# The literal start of every word, up to its first placeholder: a notice or a status line on the Conversations screen
# ("The app was closed in the background, so ...") is the app's word, often cut short, never the person's own text.
_STARTS = sorted({re.split(r'%(?:\d+\$)?[sd]|\{', v.strip())[0].strip() for v in _words.values()}, key=len, reverse=True)
_STARTS = [t for t in _STARTS if len(t) >= 12]


# Whole words long enough to be unmistakable inside a longer line (a notice built from two words).
_LONG = [w for w in CHROME if len(w) >= 20]


def apps_words(text):
    """True when [text] is the app's own words: a word, a filled-in word, or a line that holds a long word."""
    t = text.strip()
    if t in CHROME or t.rstrip('…').strip() in CHROME:
        return True
    return any(t.startswith(w) for w in _STARTS) or any(w in t for w in _LONG)


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


def row_titles(nodes, text):
    """The exact [text] labels that are a row's own title: nothing labelled sits to their left on the same line. A row's
    value can read the same as another row's title (Settings > Server shows "Servers  This phone" above the "This phone"
    row); the value has its row's title to its left, so it is left out."""
    def beside(a, b):
        return a.box[1] < b.box[3] and b.box[1] < a.box[3] and b.box[2] <= a.box[0]
    hits = find(nodes, text)
    return [h for h in hits if not any(n is not h and n.label().strip() and beside(h, n) for n in nodes)]


def tap(node):
    x, y = node.center
    adb('shell', 'input', 'tap', str(x), str(y))


def wait_for(what, timeout, test):
    """Polls the screen every 2 s until test(nodes) is truthy; -> (value, nodes). value is None on timeout."""
    # OC_SMOKE_WAIT_SCALE stretches every wait on a slow or loaded device (the result note says when it was used).
    end = time.time() + timeout * float(os.environ.get('OC_SMOKE_WAIT_SCALE', '1') or '1')
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
    # The title can be drawn before the page's own buttons on a loaded device: wait for the title AND the + button.
    new = (word('chatsHomeNewChat'), word('chatsHomeNewShort'))
    v, nodes = wait_for('Conversations', timeout,
                        lambda ns: find(ns, word('chatsHomeTitle')) and (find(ns, new[0]) or find(ns, new[1])))
    if not v:
        raise Fail(f'the app did not show "{word("chatsHomeTitle")}" within {timeout} s (is the phone unlocked and the app past its setup?)')
    return nodes


def user_texts(nodes):
    """Texts on the Conversations screen that are the person's own: a row's title (a row can be one label "title,
    status, 2 min ago", whose time changes between two looks), never one of the app's words, a time or the box."""
    out = set()
    for n in nodes:
        t = n.label().strip().split(', ')[0].strip()
        if len(t) >= 3 and not apps_words(t) and not re.search(r'\d', t) and not n.cls.endswith('EditText'):
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
        raise Fail(f'the installed build ({have}) is not older than the new one ({new_code}); an update over it is not a test of '
                   'updating: install an older build of the same signer first (adb install -r), open a conversation in it, run again')
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
    v, nodes = wait_for('Settings', 60, lambda ns: find(ns, word('settingsHubSearch'), exact=False))
    if not v:
        raise Fail('Settings did not open')
    # Settings lists the "Server" group (its second line names the server, e.g. "This phone · Connected"); the
    # "This phone" row is inside that group.
    server_row = word('settingsHubServer')
    for _ in range(8):
        hit = find(nodes, server_row)
        if hit:
            tap(hit[0])
            break
        swipe_up()
        nodes = screen()
    else:
        raise Fail(f'no "{server_row}" row in Settings')
    v, nodes = wait_for('This phone row', 20, lambda ns: row_titles(ns, word('settingsV3ThisPhone')))
    for _ in range(6):
        if v:
            tap(v[0])
            return
        swipe_up()
        v = row_titles(screen(), word('settingsV3ThisPhone'))
    raise Fail(f'no "{word("settingsV3ThisPhone")}" row under Settings > {server_row}')


def check_b(ctx):
    launch()
    conversations_screen()
    open_this_phone()
    running = word('thisPhoneAnswerRunning')
    # After an update the phone's own server can take minutes to come up on a loaded device; wait for an answer.
    v, nodes = wait_for('server state', 180, lambda ns: find(ns, running) or find(ns, word('thisPhoneStart')) or find(ns, word('thisPhoneAnswerOff')))
    if not v:
        raise Fail('the "This phone" page showed neither a running nor a stopped server within 180 s')
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
    # The edge light's list can also offer "New conversation" (on the right edge, mid screen); the page's own + sits
    # lowest on the screen, so tap the lowest match.
    tap(max(nc, key=lambda n: n.box[3]))

    def composer(ns):
        return [n for n in ns if n.cls.endswith('EditText') or n.label().strip() in (word('kitComposerField'), word('chatUiAskOpenCode'))]
    v, nodes = wait_for('composer', 90, composer)
    if not v:
        project = os.environ.get('OC_SMOKE_PROJECT', '').strip()
        hit = find(nodes, project) if project else []
        if not hit:
            raise Fail('a project has to be picked and OC_SMOKE_PROJECT does not name one on screen (set it to a project of the phone\'s own server)')
        tap(hit[0])
        v, nodes = wait_for('composer', 30, composer)
        if not v:
            raise Fail('the conversation did not open with a message box')
    choose_server(composer)
    settle_setup()
    return composer


def choose_server(composer):
    """OC_SMOKE_SERVER (optional): the saved connection, by its shown name, that checks c and d run on. New conversation
    opens on the last-used place after every relaunch, so the script picks it each time: the line under the title
    ("Where it runs: ...") opens the project sheet, "Runs on" lists the connections, then OC_SMOKE_PROJECT picks the project."""
    server = os.environ.get('OC_SMOKE_SERVER', '').strip()
    if not server:
        return
    prefix = word('chatsNewWhereSpoken').split('%')[0].strip()
    v, nodes = wait_for('where line', 30, lambda ns: [n for n in ns if n.label().strip().startswith(prefix)])
    if not v:
        raise Fail(f'no "{prefix}" line on New conversation to choose {server}')
    tap(v[0])
    v, nodes = wait_for('server row', 20, lambda ns: find(ns, server))
    if not v:
        raise Fail(f'the project sheet lists no connection named "{server}" under "{word("chatsNewRunsOn")}"')
    tap(v[0])
    project = os.environ.get('OC_SMOKE_PROJECT', '').strip()
    if project:
        v, nodes = wait_for('project row', 60, lambda ns: find(ns, project))
        if not v:
            raise Fail(f'{server} lists no project "{project}"')
        tap(v[0])
    v, nodes = wait_for('composer', 30, composer)
    if not v:
        raise Fail(f'New conversation did not come back with a message box after choosing {server}')


def settle_setup(timeout=90):
    """Each check starts the app cold, so the phone's own server may still be starting when New conversation reads its
    projects and models ("This server's projects and models couldn't be read."). The page does not read again by
    itself; a person taps "Try again". Do the same, every few seconds, until the page has read them."""
    failed, retry = word('chatsNewSetupFailed'), word('commonRetry')
    end = time.time() + timeout
    while time.time() < end:
        nodes = screen()
        if not find(nodes, failed):
            return
        again = find(nodes, retry)
        if again:
            tap(again[0])
        time.sleep(5)
    raise Fail(f'New conversation still said "{failed}" after {timeout} s of "{retry}" (the server did not answer)')


def clear_field():
    # Select all and delete: New conversation brings back an earlier draft, which must not join the prompt.
    shell('input', 'keycombination', 'KEYCODE_CTRL_LEFT', 'KEYCODE_A')
    shell('input', 'keyevent', 'KEYCODE_DEL')


def send_buttons(nodes):
    """The button that sends: "Send" in a conversation, "Start" (or "Start with <agent>") on New conversation."""
    hits = find(nodes, word('kitComposerSend')) or find(nodes, word('upgradeNewStart'))
    if not hits:
        prefix = word('chatsNewStartWith').split('%')[0].strip()
        hits = [n for n in nodes if prefix and n.label().strip().startswith(prefix)]
    return hits


def send(composer, text):
    nodes = screen()
    box = composer(nodes)
    if not box:
        raise Fail('no message box to type in')
    tap(box[0])
    time.sleep(1)
    clear_field()
    type_text(text)
    time.sleep(1)
    nodes = screen()
    btn = send_buttons(nodes)
    if not btn:
        raise Fail(f'no "{word("kitComposerSend")}" or "{word("upgradeNewStart")}" button after typing')
    tap(btn[0])


def check_c(ctx):
    composer = new_conversation()
    ctx['composer'] = composer
    tok = 'OK-' + secrets.token_hex(3)
    send(composer, f'Reply with exactly this word and nothing else: {tok}')
    v, _ = wait_for('reply', 150, lambda ns: [n for n in ns if n.label().strip() == tok])
    if not v:
        raise Fail(f'no reply that is exactly "{tok}" within 150 s (was it sent, and does the server have a working model?)')
    return 'the reply came back'


MAX_QUESTIONS = 6


def check_d(ctx):
    composer = new_conversation()  # its own conversation: check c's must not carry this prompt
    name = 'smoke-' + secrets.token_hex(3) + '.txt'
    send(composer, f'Create a file named {name} in the project with the word hello in it, in one write. Do nothing else.')
    allow = word('kitRequestAllowOnce')
    v, nodes = wait_for('permission question', 120, lambda ns: find(ns, allow))
    if not v:
        raise Fail(f'no permission question ("{allow}") within 120 s (is the permission mode "ask" and the model able to write files?)')
    answered = 0
    while v and answered < MAX_QUESTIONS:
        tap(v[0])
        answered += 1
        time.sleep(3)
        # The agent may ask again (read the file back, list the folder): answer each one, then wait for quiet.
        v, nodes = wait_for('another question', 20, lambda ns: find(ns, allow))
    if v:
        raise Fail(f'a permission question was still there after "{allow}" was pressed {answered} times')
    mentions = lambda ns: [n for n in ns if name in n.label()]
    v, nodes = wait_for('run goes on', 120, lambda ns: len(mentions(ns)) >= 2)
    if not v:
        raise Fail(f'after "Allow once" nothing new about {name} appeared within 120 s (the run did not go on)')
    return f'{answered} permission question(s) answered with Allow once, the run went on'


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
    commit = subprocess.run(['git', '-C', REPO, 'rev-parse', '--verify', '--quiet', (a.commit or 'HEAD') + '^{commit}'],
                            capture_output=True, text=True).stdout.strip()
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        die(f'could not read the commit {a.commit or "HEAD"} in this repository (fetch it, or pass --commit <sha>)')
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
