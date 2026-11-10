#!/usr/bin/env python3
"""Private BB9 emulator batch. No device action occurs on import."""
import argparse
import copy
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import sys
import time
from urllib.parse import urlsplit
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tool/qa'))
import bb3_runtime_acceptance as Q

PRIVATE, PACKAGE, SERIAL, NATIVE = Q.PRIVATE, Q.PACKAGE, Q.SERIAL, Q.NATIVE
FLUTTER = PRIVATE + '/shared_prefs/FlutterSharedPreferences.xml'
WRITER = PRIVATE + '/shared_prefs/builtin_component_writer.xml'
FIXTURE = PRIVATE + '/files/bb9-runtime-qa.json'
UBUNTU = PRIVATE + '/files/linux/ubuntu'
ACTIVE = UBUNTU + '/opt/opencode2/bin/opencode2'
PENDING = UBUNTU + '/opt/opencode2.oc-pending'
EXTERNAL_EXPORT = PRIVATE + '/files/bb9-external-qa.json'
LEGACY_LOCK = UBUNTU + '/home/oc/.local/share/oc-agents/.lock-claude'
STEPS = {'bb9Preflight': 'bb9PreflightPassed', 'bb9PrepareInterrupted': 'bb9InterruptedReady',
         'bb9Verify': 'bb9Verified', 'bb9Cleanup': 'bb9CleanupComplete',
         'bb9SerializedChecks': 'bb9SerializedChecksPassed', 'bb9LivePeers': 'bb9LivePeersPassed',
         'bb9PrepareExternal': 'bb9ExternalPrepared', 'bb9ExternalCommit': 'bb9ExternalCommitted',
         'bb9ExternalArmed': 'bb9ExternalArmed', 'bb9FilesystemCases': 'bb9FilesystemCasesPassed', 'bb9FilesystemCleanup': 'bb9FilesystemCleanupComplete'}
FILESYSTEM_FIELDS = {'bb9FirstInstallAbsentPassed', 'bb9UnsafeReceiptRefused',
                     'bb9UnknownQuiescenceRefused', 'bb9LegacyLockRefused'}
FIELDS = Q.SAFE_FIELDS | set(STEPS.values()) | FILESYSTEM_FIELDS | {'bb9OriginalGoodPresent', 'bb9OwnedWriterGone', 'bb9PeerSurvivedCompletion', 'bb9VerifierAppPid', 'bb9VerifierAppStartTicks'}
require = Q.require


def identity(value):
    require(isinstance(value, dict) and set(value) == {'pid', 'startTicks', 'parent', 'group', 'session'}, 'fixture_identity_invalid')
    require(all(type(n) is int for n in value.values()) and value['pid'] > 1 and value['startTicks'] > 0 and
            value['parent'] >= 0 and value['group'] > 0 and value['session'] >= 0, 'fixture_identity_invalid')
    return value


def fixture(raw):
    require(isinstance(raw, str) and len(raw.encode()) <= 131072, 'fixture_overflow')
    value = json.loads(raw)
    required = {'version', 'stage', 'app', 'originalExecutableSha256', 'originalGoodPresent',
                'originalLockPresent', 'ticket', 'activityAbsent'}
    extra = {'originalGoodDevice', 'originalGoodInode'}
    require(isinstance(value, dict) and required <= value.keys() and value.keys() <= required | extra and
            type(value['version']) is int and value['version'] == 1 and value['stage'] == 'armed' and
            value['activityAbsent'] is True and value['originalLockPresent'] is False and
            type(value['originalGoodPresent']) is bool and re.fullmatch('[a-f0-9]{64}', value['originalExecutableSha256']), 'fixture_schema_invalid')
    identity(value['app'])
    require((extra <= value.keys()) if value['originalGoodPresent'] else not (extra & value.keys()), 'fixture_original_good_invalid')
    if value['originalGoodPresent']:
        require(all(type(value[k]) is int and value[k] >= 0 for k in extra), 'fixture_original_good_invalid')
    ticket = value['ticket']
    require(isinstance(ticket, dict) and set(ticket) == {'version', 'id', 'rootfsGeneration', 'targets', 'operation', 'ownership', 'observed'} and
            type(ticket['version']) is int and ticket['version'] == 1 and ticket['operation'] == 'INSTALL' and
            all(isinstance(ticket[k], str) and re.fullmatch('[a-f0-9]{64}', ticket[k]) for k in ['id', 'rootfsGeneration']), 'fixture_ticket_invalid')
    targets = ['OPENCODE1', 'OPENCODE2', 'PASEO', 'CLAUDE', 'LEGACY_CLAUDE']
    require(isinstance(ticket['targets'], list) and len(ticket['targets']) == 5 and set(ticket['targets']) == set(targets), 'fixture_targets_invalid')
    receipt = ticket['ownership']
    require(isinstance(receipt, dict) and set(receipt) == {'version', 'boot', 'nonce', 'generation', 'root', 'leader', 'other'} and
            type(receipt['version']) is int and receipt['version'] == 1 and
            isinstance(receipt['boot'], str) and re.fullmatch('[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', receipt['boot']) and
            isinstance(receipt['nonce'], str) and re.fullmatch('[a-f0-9]{64}', receipt['nonce']) and
            type(receipt['generation']) is int and receipt['generation'] > 0, 'fixture_ownership_invalid')
    root, leader = identity(receipt['root']), identity(receipt['leader'])
    require(root['pid'] != leader['pid'] and leader['pid'] == leader['group'] == leader['session'], 'fixture_leader_invalid')
    for key, items in [('other', receipt['other']), ('observed', ticket['observed'])]:
        require(isinstance(items, list) and len(items) <= 128 and all(identity(i) for i in items) and
                len({i['pid'] for i in items}) == len(items), 'fixture_inventory_invalid')
    require(ticket['observed'] and not {i['pid'] for i in ticket['observed']} & {i['pid'] for i in receipt['other']}, 'fixture_inventory_invalid')
    for item in ticket['observed']:
        for fixed in [root, leader]:
            require(item['pid'] != fixed['pid'] or item['startTicks'] == fixed['startTicks'], 'fixture_pid_reused')
    require(value['app']['pid'] not in {i['pid'] for i in [root, leader, *ticket['observed']]}, 'fixture_app_is_writer')
    return value


def members(value):
    ticket = value['ticket']; receipt = ticket['ownership']
    return list({(i['pid'], i['startTicks']): i for i in [receipt['root'], receipt['leader'], *ticket['observed']]}.values())


def parse_status(raw):
    result = {}
    for line in raw.splitlines():
        match = re.fullmatch(r'INSTRUMENTATION_(?:STATUS|RESULT): ([A-Za-z0-9]+)=([A-Za-z0-9_-]+)', line)
        if match and match[1] in FIELDS:
            result[match[1]] = match[2]
    return result


PHASE_NAMES = {'real_start', 'person_restore'} | {
    'instrument_' + step + suffix
    for step in set(STEPS) | {'bb3Preflight', 'bb3Capabilities'}
    for suffix in ['', '_identity_check', '_pre_detach', '_invoke', '_post_detach']
}


def phase_category(error):
    if isinstance(error, Q.Refused):
        if str(error) in {'instrumentation_replaced_app', 'instrument_requires_live_app'}:
            return 'identity_changed'
        if str(error) in {'instrumentation_not_detached', 'instrumentation_state_unavailable'}:
            return 'detachment_unavailable'
        return 'refused'
    if isinstance(error, TimeoutError) or type(error).__name__ == 'TimeoutExpired':
        return 'timeout'
    if isinstance(error, (ValueError, TypeError, KeyError, ET.ParseError)):
        return 'invalid_data'
    return 'unavailable'


def phase_record(device, name, outcome, category=None):
    require(name in PHASE_NAMES and outcome in {'before', 'after', 'failure'}, 'phase_invalid')
    sink = getattr(device, '_phase_evidence', None)
    if sink is None:
        return
    count = getattr(device, '_phase_count', 0)
    if count >= 256:
        return
    device._phase_count = count + 1
    line = 'phase=' + name + ' outcome=' + outcome
    if category is not None:
        require(category in {'identity_changed', 'detachment_unavailable', 'refused', 'timeout', 'invalid_data', 'unavailable'}, 'phase_category_invalid')
        line += ' category=' + category
    sink.append(line)
    print(line, flush=True)


def traced(device, name, action):
    phase_record(device, name, 'before')
    try:
        result = action()
    except Exception as error:
        phase_record(device, name, 'failure', phase_category(error))
        raise
    phase_record(device, name, 'after')
    return result


class Device(Q.Device):
    def adb(self, *args, timeout=8):
        return super().adb(*args, timeout=timeout)

    def cat(self, path, required=True):
        # Device-side cap avoids retaining arbitrary large private data on the host.
        result = self.adb('shell', 'sh', '-c', shlex.quote('head -c 1048577 ' + shlex.quote(path) + ' 2>/dev/null'), timeout=3)
        require(len(result.stdout.encode()) <= 1048576, 'private_snapshot_overflow')
        if required:
            require(result.returncode == 0, 'private_snapshot_unavailable')
        return result.stdout if result.returncode == 0 else None

    def instrument(self, step, *extra, expected_app=None, expected_failure=None):
        require(step in STEPS or step in {'bb3Preflight', 'bb3Capabilities'}, 'instrument_step_invalid')
        return traced(self, 'instrument_' + step, lambda: self._instrument(step, extra, expected_app, expected_failure))

    def _instrument(self, step, extra, expected_app, expected_failure):
        app = expected_app or self.app_identity()
        current = self.app_identity()
        traced(self, 'instrument_' + step + '_identity_check',
               lambda: require(app and Q.same_process(app, current), 'instrument_requires_live_app'))
        traced(self, 'instrument_' + step + '_pre_detach', lambda: self.wait_detached(app))
        result = traced(self, 'instrument_' + step + '_invoke', lambda:
                       self.adb('shell', 'am', 'instrument', '--no-restart', '-w', '-e', 'step', step, *extra, Q.RUNNER, timeout=90))
        fields = parse_status(result.stdout)
        fixed_reason = fields.get('builtinRuntimeFailure')
        if step == 'bb5Idle' and fields.get('builtinRuntimeResult') == 'FAIL':
            for key in ['bb5RuntimePrepared', 'bb5IdleWaitEntered']:
                print('native_qa_' + key + '=' + ('true' if fields.get(key) == 'true' else 'unproven'), flush=True)
            cleanup_reason = fields.get('bb5CleanupFailure')
            if isinstance(cleanup_reason, str) and re.fullmatch(r'bb5_[a-z_]{1,80}', cleanup_reason):
                print('native_qa_cleanup_refusal=' + cleanup_reason, flush=True)
        if fields.get('builtinRuntimeResult') != 'PASS' and isinstance(fixed_reason, str) and re.fullmatch(r'(?:bb5|bb9)_[a-z_]{1,80}', fixed_reason):
            print('native_qa_refusal=' + fixed_reason, flush=True)
        if expected_failure is not None:
            require(step in {'bb9SerializedChecks', 'bb9LivePeers'} and expected_failure ==
                    ('bb9_serialized_checks_failed' if step == 'bb9SerializedChecks' else 'bb9_live_peer_failed'),
                    'expected_failure_invalid')
            require(result.returncode == 0 and fields.get('builtinRuntimeResult') == 'FAIL' and
                    fixed_reason == expected_failure and 'INSTRUMENTATION_CODE: -1' in result.stdout,
                    'expected_native_failure_unproven')
            traced(self, 'instrument_' + step + '_post_detach', lambda: self.wait_detached(app))
            return fields
        if fields.get('builtinRuntimeResult') == 'FAIL' and isinstance(fixed_reason, str) and re.fullmatch(
                r'bb5_[a-z_]{1,80}', fixed_reason):
            raise Q.Refused(fixed_reason)
        require(result.returncode == 0 and fields.get('builtinRuntimeResult') == 'PASS' and
                'INSTRUMENTATION_CODE: -1' in result.stdout, 'instrument_failed')
        traced(self, 'instrument_' + step + '_post_detach', lambda: self.wait_detached(app))
        if step in STEPS:
            require(fields.get(STEPS[step]) == 'true', 'native_step_not_proven')
        return fields

    def write_dead(self, path, raw):
        require(path in {NATIVE, FLUTTER} and self.app_identity() is None, 'metadata_writer_active')
        require(self.adb('shell', 'test', '!', '-e', path + '.bak').returncode == 0, 'metadata_backup_pending')
        command = 'cat > ' + shlex.quote(path)
        result = self.run(['adb', '-s', SERIAL, 'shell', 'sh', '-c', shlex.quote(command)], input=raw, text=True, timeout=5)
        require(result.returncode == 0 and self.app_identity() is None, 'metadata_write_failed')

    def executable_hash(self):
        result = self.adb('shell', 'sha256sum', ACTIVE, timeout=5)
        require(result.returncode == 0 and re.fullmatch('[a-f0-9]{64}\\s+[^\\n]+\\n?', result.stdout), 'executable_hash_unavailable')
        return result.stdout.split()[0]

    def recovery_complete(self, digest):
        if self.executable_hash() != digest:
            return False
        link = self.adb('shell', 'readlink', UBUNTU + '/usr/local/bin/opencode2', timeout=3)
        expected = {'/opt/opencode2/bin/opencode2', ACTIVE,
                    '/data/data/' + PACKAGE + '/files/linux/ubuntu/opt/opencode2/bin/opencode2'}
        if link.returncode != 0 or link.stdout.strip() not in expected:
            return False
        for path in [PENDING, LEGACY_LOCK]:
            if self.adb('shell', 'test', '!', '-e', path).returncode != 0:
                return False
        writer = self.cat(WRITER, required=False)
        return writer is None or 'ticket' not in Q.preference_values(ET.fromstring(writer))


def selected_profile(raw):
    values = Q.preference_values(ET.fromstring(raw))
    require('flutter.oc.profiles' in values, 'profiles_unavailable')
    profiles = json.loads(values['flutter.oc.profiles'].text or '')
    require(isinstance(profiles, list) and len(profiles) <= 128, 'profile_inventory_invalid')
    found = []
    for profile in profiles:
        require(isinstance(profile, dict), 'profile_inventory_invalid')
        parsed = urlsplit(profile.get('baseUrl', ''))
        if profile.get('flavor') == 'v2' and profile.get('backend', 'openCode') == 'openCode' and parsed.hostname == '127.0.0.1' and parsed.port == 4097:
            found.append(profile.get('id'))
    require(len(found) == 1 and isinstance(found[0], str) and re.fullmatch('[A-Za-z0-9_-]{1,80}', found[0]), 'managed_profile_ambiguous')
    return found[0]


def native_armed(raw, owner):
    values = Q.preference_values(ET.fromstring(raw))
    return all(values.get(k) is not None and values[k].text == owner for k in ['owner', 'restoreOwner']) and \
        all(values.get(k) is not None and values[k].attrib.get('value') == v for k, v in [('enabled', 'true'), ('wanted', 'true')]) and \
        all(k + owner in values for k in ['oc.builtinRuntimeRecipe.', 'oc.builtinRuntimeOwnership.'])


def real_start(device, owner, evidence, require_explicit=False):
    require(device.adb('shell', 'am', 'start', '-n', PACKAGE + '/.MainActivity').returncode == 0, 'normal_activity_failed')
    device.ensure_normal_app()
    locales = [json.loads((ROOT / ('lib/l10n/app_' + lang + '.arb')).read_text()) for lang in ['en', 'ar']]
    labels = lambda *keys: {v[k] for v in locales for k in keys if k in v}
    ui = '/data/local/tmp/oc-bb9-' + os.urandom(8).hex() + '.xml'
    started, navigated = False, False
    deadline = time.monotonic() + 150
    try:
        while time.monotonic() < deadline:
            require(device.adb('shell', 'uiautomator', 'dump', ui, timeout=12).returncode == 0, 'normal_ui_unavailable')
            nodes = list(ET.fromstring(device.cat(ui)).iter('node'))
            def tap(keys):
                chosen = [n for n in nodes if any(s.strip() in labels(*keys) for k in ['text', 'content-desc'] for s in n.attrib.get(k, '').splitlines())]
                require(len(chosen) <= 1, 'normal_ui_action_ambiguous')
                if not chosen:
                    return False
                b = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', chosen[0].attrib.get('bounds', ''))
                require(b, 'normal_ui_bounds_invalid')
                a, c, e, f = map(int, b.groups())
                require(device.adb('shell', 'input', 'tap', str((a + e)//2), str((c + f)//2)).returncode == 0, 'normal_ui_tap_failed')
                return True
            visible = [n.attrib.get('text', '') + '\n' + n.attrib.get('content-desc', '') for n in nodes]
            if (started or not require_explicit) and Q.connected_open_code_two(visible, labels('serverRowConnected', 'e7WorkspaceConnected')) and device.healthy() and native_armed(device.cat(NATIVE), owner):
                evidence.append('PASS actual_normal_MainActivity_authenticated_Connected_OC2' + ('_explicit_Start' if started else '_restored'))
                return
            if not started:
                started = tap(['phoneServerStart', 'phoneServerStartAndConnect'])
                if not started and not navigated and tap(['serverSwitcherOpen']):
                    time.sleep(.5); navigated = True
                elif not started:
                    tap(['serverSwitcherManage', 'phoneServerConnect', 'phoneSetupStartConnect'])
            time.sleep(.5)
        raise Q.Refused('normal_connected_restore_unproven')
    finally:
        require(device.adb('shell', 'rm', '-f', ui).returncode == 0, 'normal_ui_cleanup_failed')


def uid_inventory(device, uid):
    result = device.adb('shell', 'ps', '-A', '-o', 'PID,PPID,UID', timeout=3)
    require(result.returncode == 0 and len(result.stdout) <= 262144, 'bootstrap_inventory_unavailable')
    rows = {}
    for line in result.stdout.splitlines():
        parts = line.split()
        if parts == ['PID', 'PPID', 'UID']:
            continue
        require(len(parts) == 3 and all(p.isdecimal() for p in parts), 'bootstrap_inventory_invalid')
        pid, parent, current_uid = map(int, parts)
        if current_uid == uid:
            require(pid not in rows, 'bootstrap_inventory_invalid')
            rows[pid] = parent
    require(len(rows) <= 128, 'bootstrap_inventory_overflow')
    return rows


def qualified_payload_root(argv, maps, parent):
    private_roots = [UBUNTU, '/data/data/' + PACKAGE + '/files/linux/ubuntu']
    proot = bool(argv and re.fullmatch(r'/data/app/[A-Za-z0-9_./+=~-]+/lib/(?:arm64|x86_64)/libproot\.so', argv[0]) and
                 any('--rootfs=' + root in argv for root in private_roots))
    mapped = False
    if parent == 1 and not proot:
        executables = {root + suffix for root in private_roots for suffix in
                       ['/opt/opencode2/bin/opencode2', '/opt/opencode2', '/opt/opencode/bin/opencode']}
        mapped = any(len(parts := line.split(None, 5)) == 6 and 'x' in parts[1] and
                     parts[5].removesuffix(' (deleted)') in executables for line in maps.splitlines())
    return proot or mapped


def bootstrap(device, args, evidence):
    metadata = device.adb('shell', 'cmd', 'package', 'list', 'packages', '-U', '--user', '0', PACKAGE, timeout=3)
    uids = re.findall(r'^package:' + re.escape(PACKAGE) + r' uid:(\d+)$', metadata.stdout, re.M)
    require(metadata.returncode == 0 and len(uids) == 1 and int(uids[0]) >= 10000, 'bootstrap_uid_unconfirmed')
    uid = int(uids[0]); rows = uid_inventory(device, uid); app = device.app_identity(); records = {}; roots = set()
    for pid, parent in rows.items():
        current = device.identity(pid)
        require(current is not None and current['parent'] == parent, 'bootstrap_inventory_changed')
        records[pid] = current
        if app and Q.same_process(app, current):
            continue
        raw = device.cat('/proc/' + str(pid) + '/cmdline')
        require(len(raw.encode()) <= 65536, 'bootstrap_argv_overflow')
        argv = raw.rstrip('\x00').split('\x00')
        require(not any(Path(a).name in {'apt', 'apt-get', 'dpkg', 'npm', 'pip', 'wget', 'curl'} for a in argv) and
                not any(argv[i:i+2] in [['auth', 'login'], ['codex', 'login']] for i in range(len(argv))), 'bootstrap_writer_or_signin_refused')
        maps = device.cat('/proc/' + str(pid) + '/maps', required=False) if parent == 1 else ''
        require(len((maps or '').encode()) <= 1048576, 'bootstrap_maps_overflow')
        if qualified_payload_root(argv, maps or '', parent):
            roots.add(pid)
    covered = roots | ({app['pid']} if app else set())
    for _ in range(len(rows)):
        covered |= {pid for pid, parent in rows.items() if parent in covered}
    require(set(rows) <= covered, 'bootstrap_unknown_uid_payload_refused')
    if app:
        require(Q.same_process(app, device.app_identity()), 'bootstrap_app_changed')
        stop = device.adb('shell', 'am', 'startservice', '-n', PACKAGE + '/.BuiltinServerService', '-a', 'stop', timeout=12)
        require(stop.returncode == 0 and 'Error' not in stop.stdout and 'Error' not in stop.stderr, 'bootstrap_stop_failed')
    require(device.adb('shell', 'am', 'force-stop', PACKAGE).returncode == 0, 'bootstrap_force_stop_failed')
    payloads = set(roots)
    for _ in range(len(rows)):
        payloads |= {pid for pid, parent in rows.items() if parent in payloads}
    ordered = sorted(payloads, key=lambda pid: pid in roots)
    for sig in ['TERM', 'KILL']:
        for pid in ordered:
            expected = records[pid]
            if not Q.same_process(expected, device.identity(pid)):
                continue
            status = device.cat('/proc/' + str(pid) + '/status')
            found = re.findall(r'^Uid:\s+(\d+)', status, re.M)
            require(found == [str(uid)], 'bootstrap_uid_changed')
            # Recheck UID and ticks in the same shell immediately before signaling.
            command = (f"u=$(sed -n 's/^Uid:[[:space:]]*\\([0-9]*\\).*/\\1/p' /proc/{pid}/status) || exit 1; "
                       f"[ \"$u\" = '{uid}' ] || exit 1; s=$(cat /proc/{pid}/stat) || exit 0; "
                       f"s=${{s##*) }}; set -- $s; shift 19; [ \"$1\" = '{expected['startTicks']}' ] || exit 0; kill -{sig} {pid}")
            require(device.adb('shell', 'sh', '-c', shlex.quote(command), timeout=3).returncode == 0, 'bootstrap_exact_signal_failed')
        time.sleep(.2)
    require(not uid_inventory(device, uid), 'bootstrap_uid_not_quiescent')
    for apk in [args.runner_apk, args.apk]:
        flags = ['-r', '-d'] if apk == args.apk and getattr(args, 'qa_normal_downgrade', False) is True else ['-r']
        result = device.adb('install', *flags, str(apk.resolve()), timeout=60)
        require(result.returncode == 0 and 'Success' in result.stdout, 'bootstrap_in_place_install_failed')
    evidence.append('PASS bounded_exact_payload_bootstrap_same_signer_in_place_data_preserved')


def validate_candidates(device, args):
    require(type(args.version) is int and args.version > 0, 'candidate_version_invalid')
    for apk, expected, package in [(args.apk, args.target_sha, PACKAGE), (args.runner_apk, args.runner_sha, PACKAGE + '.test')]:
        require(isinstance(expected, str) and re.fullmatch('[a-f0-9]{64}', expected), 'candidate_hash_invalid')
        with apk.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        require(digest == expected, 'candidate_hash_mismatch')
        signature = device.run([str(args.apksigner), 'verify', '--print-certs', str(apk)], text=True, timeout=15)
        found = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)', signature.stdout)
        require(signature.returncode == 0 and found == [Q.CERT], 'candidate_signer_mismatch')
        badging = device.run([str(args.aapt), 'dump', 'badging', str(apk)], text=True, timeout=15)
        match = re.search(r"package: name='([^']+)' versionCode='(\d*)'", badging.stdout)
        require(badging.returncode == 0 and match and match[1] == package and (match[2] == str(args.version) if package == PACKAGE else (match[2] == '' or int(match[2]) > 0)), 'candidate_metadata_mismatch')
    installed = device.adb('shell', 'dumpsys', 'package', PACKAGE)
    codes = re.findall(r'\bversionCode=(\d+)', installed.stdout)
    require(installed.returncode == 0 and codes and len(set(codes)) == 1, 'candidate_version_unavailable')
    if int(codes[0]) > args.version:
        require(getattr(args, 'qa_normal_downgrade', False) is True and
                type(getattr(args, 'normal_version', None)) is int and int(codes[0]) == args.normal_version and
                re.fullmatch('[a-f0-9]{64}', getattr(args, 'normal_sha', '')) and
                device.installed_hash(PACKAGE) == args.normal_sha,
                'candidate_downgrade_refused')


class Session:
    def __init__(self, device, original, owner, evidence):
        self.device, self.original, self.owner, self.evidence = device, original, owner, evidence
        self.prepared = False; self.saved = None

    def run(self):
        d = self.device
        d.instrument('bb9Preflight')
        self.prepared = True  # Partial prepare still requires native cleanup.
        d.instrument('bb9PrepareInterrupted')
        self.saved = fixture(d.cat(FIXTURE))
        self.cold_cycle()

    def cold_cycle(self, require_orphan=False):
        d = self.device
        app = self.saved['app']
        require(Q.same_process(app, d.app_identity()) and d.activity_absent(), 'fixture_app_not_detached')
        d.wait_detached(app)
        writer = d.cat(WRITER)
        writer_values = Q.preference_values(ET.fromstring(writer))
        require('ticket' in writer_values and 'rootfsGeneration' in writer_values and
                writer_values['rootfsGeneration'].text == self.saved['ticket']['rootfsGeneration'] and
                json.loads(writer_values['ticket'].text) == self.saved['ticket'], 'fixture_writer_changed')
        private_fixture = d.cat(FIXTURE)
        require(Q.same_process(app, d.app_identity()) and d.detached(app) and d.activity_absent(), 'fixture_changed_before_kill')
        d.signal(app, 'KILL')
        deadline = time.monotonic() + 8
        while Q.same_process(app, d.app_identity()) and time.monotonic() < deadline:
            time.sleep(.1)
        require(d.app_identity() is None, 'fixture_app_not_dead')
        survivors = sum(Q.same_process(i, d.identity(i['pid'])) for i in members(self.saved))
        self.evidence.append('observed_writer_survivors=' + str(survivors))
        self.evidence.append('writer_identities_no_longer_live=' + str(len(members(self.saved)) - survivors))
        if require_orphan:
            receipt = self.saved['ticket']['ownership']
            require(all(Q.same_process(i, d.identity(i['pid'])) for i in [receipt['root'], receipt['leader']]),
                    'orphan_installer_survival_unproven')
            self.evidence.append('PASS actual_original_installer_root_and_leader_survived_app_SIGKILL')
        # Do not restore the component writer preferences or any component fault journal.
        d.write_dead(NATIVE, self.original)
        require(d.cat(WRITER) == writer and d.cat(FIXTURE) == private_fixture, 'writer_journal_changed_by_host')
        self.evidence.append('PASS original_wanted_policy_snapshot_restored_while_app_dead_writer_journal_preserved')
        traced(d, 'real_start', lambda: real_start(d, self.owner, self.evidence))
        require(d.recovery_complete(self.saved['originalExecutableSha256']), 'actual_cold_start_recovery_unproven')
        require(all(not Q.same_process(i, d.identity(i['pid'])) for i in members(self.saved)), 'actual_cold_start_writer_not_drained')
        self.evidence.append('PASS actual_product_cold_start_restored_prior_good_before_read_only_Verify')
        result = d.instrument('bb9Verify')
        require(result.get('bb9Verified') == 'true', 'native_recovery_verify_unproven')
        require(result.get('bb9OwnedWriterGone') == 'true', 'native_writer_drain_unproven')
        actor = getattr(self, 'external', None)
        acknowledge = getattr(actor, 'acknowledge_recovered_app', None)
        if acknowledge is not None:
            current = d.app_identity()
            require(current is not None and result.get('bb9VerifierAppPid') == str(current['pid']) and
                    result.get('bb9VerifierAppStartTicks') == str(current['startTicks']),
                    'native_recovered_reader_unproven')
            acknowledge(current, int(result['bb9VerifierAppPid']), int(result['bb9VerifierAppStartTicks']))
            confirm = getattr(actor, 'acknowledge_native_drain', None)
            require(callable(confirm), 'native_drain_ack_unavailable')
            confirm(result['bb9Verified'] == 'true', result['bb9OwnedWriterGone'] == 'true')
        self.evidence.append('PASS native_read_only_Verify_prior_good_and_writer_quiescence')

    def cleanup(self):
        if self.prepared and self.device.cat(FIXTURE, required=False) is not None:
            self.device.ensure_normal_app()
            self.device.instrument('bb9Cleanup')
            self.evidence.append('PASS native_fixture_cleanup_original_good_restored')


class ExternalSession(Session):
    def __init__(self, device, original, owner, evidence):
        super().__init__(device, original, owner, evidence)
        self.external = None

    def run(self):
        from bb9_external_installer import ExternalInstaller
        d = self.device
        d.instrument('bb9Preflight')
        self.prepared = True
        d.instrument('bb9PrepareExternal')
        self.external = ExternalInstaller(d)
        try:
            observed = self.external.start(json.loads(d.cat(EXTERNAL_EXPORT)))
        except Exception:
            self.evidence.append('external_launch_diagnostics=' + json.dumps(self.external.failure_diagnostics(), sort_keys=True))
            raise
        extra = ['-e', 'ticketId', self.external.export['ticketId']]
        for kind in ['root', 'leader']:
            for suffix, key in [('Pid', 'pid'), ('StartTicks', 'startTicks')]:
                extra += ['-e', kind + suffix, str(observed[kind][key])]
        d.instrument('bb9ExternalCommit', *extra)
        values = Q.preference_values(ET.fromstring(d.cat(WRITER)))
        require('ticket' in values, 'external_native_commit_absent')
        self.external.mark_committed(json.loads(values['ticket'].text))
        self.evidence.append('PASS native_independent_kernel_nonce_signal_inventory_and_durable_commit_before_permit')
        self.external.permit()
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            if d.adb('shell', 'test', '-f', UBUNTU + '/opt/.oc-bb9-qa-ready', timeout=2).returncode == 0:
                break
            time.sleep(.1)
        d.instrument('bb9ExternalArmed')
        self.saved = fixture(d.cat(FIXTURE))
        self.cold_cycle(require_orphan=True)
        self.evidence.append('PASS actual_cold_product_start_drained_surviving_owned_installer_before_rollback')

    def cleanup(self):
        drain_error = None
        if self.external:
            try:
                self.external.drain()
            except Exception as error:
                drain_error = error
        # Native cleanup independently refuses unproven quiescence. A failed
        # host observation must not skip its opportunity to prove safe cleanup.
        super().cleanup()
        if drain_error:
            raise drain_error


class ConcurrentSession:
    def __init__(self, device, args, evidence):
        self.device, self.args, self.evidence = device, args, evidence
        self.saved = None
    def run(self):
        step = 'bb9SerializedChecks' if self.args.scenario == 'queue' else 'bb9LivePeers'
        result = self.device.instrument(step, expected_failure=self.args.expected_native_failure)
        if self.args.expected_native_failure:
            self.evidence.append('PASS expected_native_regression_failure=' + self.args.expected_native_failure)
        else:
            require(result.get('bb9SerializedChecksPassed') == 'true', 'serialized_checks_unproven')
            if self.args.scenario == 'peer':
                require(result.get('bb9PeerSurvivedCompletion') == 'true', 'live_peer_survival_unproven')
            self.evidence.append('PASS native_concurrent_' + self.args.scenario)
        self.cleanup()
    def cleanup(self):
        for name in ['.oc-bb9-live-ready', '.oc-bb9-live-done', '.oc-bb9-live-second']:
            require(self.device.adb('shell', 'test', '!', '-e', UBUNTU + '/tmp/' + name).returncode == 0,
                    'concurrent_marker_cleanup_unproven')
        raw = self.device.cat(WRITER, required=False)
        require(raw is None or 'ticket' not in Q.preference_values(ET.fromstring(raw)), 'concurrent_ticket_cleanup_unproven')
        metadata = self.device.adb('shell', 'cmd', 'package', 'list', 'packages', '-U', '--user', '0', PACKAGE)
        uids = re.findall(r'^package:' + re.escape(PACKAGE) + r' uid:(\d+)$', metadata.stdout, re.M)
        app = self.device.app_identity()
        require(metadata.returncode == 0 and len(uids) == 1 and app is not None and
                set(uid_inventory(self.device, int(uids[0]))) <= {app['pid']}, 'concurrent_kernel_cleanup_unproven')
        self.evidence.append('PASS concurrent_fixture_exact_cleanup_full_uid_quiescent')


class FilesystemSession:
    def __init__(self, device, args, evidence):
        self.device, self.evidence = device, evidence
        self.saved = None
        self.prepared = False

    def run(self):
        self.prepared = True
        result = self.device.instrument('bb9FilesystemCases')
        require(all(result.get(field) == 'true' for field in FILESYSTEM_FIELDS),
                'filesystem_cases_unproven')
        self.evidence.append('PASS real_AndroidFs_first_install_absent_invalid_receipts_unknown_quiescence_and_nonempty_lock_refusals')

    def cleanup(self):
        if self.prepared:
            self.device.ensure_normal_app()
            self.device.instrument('bb9FilesystemCleanup')
            self.evidence.append('PASS exact_filesystem_fixture_cleanup_preserved_installed_programs')


def inherited_lock(fd=None):
    stat = os.stat('/home/eslam/Storage/tmp/oc-emulator.lock')
    if fd is None:
        found = []
        for path in Path('/proc/self/fd').iterdir():
            if path.name.isdecimal() and int(path.name) >= 3:
                try:
                    current = os.fstat(int(path.name))
                    if (current.st_dev, current.st_ino) == (stat.st_dev, stat.st_ino):
                        found.append(int(path.name))
                except OSError:
                    pass
        require(len(found) == 1, 'inherited_emulator_lock_unconfirmed')
        fd = found[0]
    require(type(fd) is int and fd >= 3, 'inherited_emulator_lock_invalid')
    current = os.fstat(fd)
    require((current.st_dev, current.st_ino) == (stat.st_dev, stat.st_ino), 'inherited_emulator_lock_wrong_file')
    fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    return fd


def preference_equal(before, after):
    # Android may rewrite XML indentation. Compare the actual typed preference value.
    def value(node):
        return None if node is None else (node.tag, tuple(sorted(node.attrib.items())), node.text)
    return value(before) == value(after)


def execute(device, args, evidence):
    device._phase_evidence = evidence
    device._phase_count = 0
    validate_candidates(device, args)
    require(device.adb('get-state').stdout.strip() == 'device', 'emulator_unavailable')
    original_flutter = device.cat(FLUTTER)
    owner = selected_profile(original_flutter)
    original = device.cat(NATIVE); baseline = None; session = None; primary = None; restore_error = None
    try:
        if device.installed_hash(PACKAGE) != args.target_sha or device.installed_hash(PACKAGE + '.test') != args.runner_sha:
            bootstrap(device, args, evidence)
        require(device.installed_hash(PACKAGE) == args.target_sha and device.installed_hash(PACKAGE + '.test') == args.runner_sha, 'installed_candidate_mismatch')
        # Selecting the existing profile changes only its active-profile pointer while dead.
        tree = ET.fromstring(original_flutter); values = Q.preference_values(tree)
        if values.get('flutter.oc.activeProfile') is None or values['flutter.oc.activeProfile'].text != owner:
            require(device.app_identity() is None, 'existing_active_profile_writer_live')
            node = values.get('flutter.oc.activeProfile')
            if node is None:
                node = ET.SubElement(tree, 'string', {'name': 'flutter.oc.activeProfile'})
            node.text = owner; device.write_dead(FLUTTER, ET.tostring(tree, encoding='unicode'))
        traced(device, 'real_start', lambda: real_start(device, owner, evidence))
        original = device.cat(NATIVE)
        require(native_armed(original, owner) and Q.baseline_policy_valid(ET.fromstring(original), ET.fromstring(device.cat(FLUTTER))) and
                device.healthy() and device.service_state(), 'canonical_owner_baseline_unproven')
        baseline = device.instrument('bb3Preflight')
        require(baseline.get('installedCertificateSha256') == Q.CERT and baseline.get('installedVersion') == str(args.version) and
                baseline.get('installedRuntimeQa') == 'true' and baseline.get('baselinePolicyMarkerValid') == 'true', 'installed_native_preflight_unproven')
        scenario = getattr(args, 'scenario', 'cold')
        session = (ExternalSession(device, original, owner, evidence) if scenario == 'orphan' else
                   Session(device, original, owner, evidence)) if scenario in {'cold', 'orphan'} else (
            FilesystemSession(device, args, evidence) if scenario == 'fs' else ConcurrentSession(device, args, evidence))
        session.run()
    except Exception as error:
        primary = error
        try:
            app = device.app_identity()
            if app is not None:
                # Read-only native projection retains the same captured-app guard as acceptance.
                fields = device.instrument('bb3Capabilities', expected_app=app)
                safe = {k: v for k, v in fields.items() if k in Q.SAFE_FIELDS and k.startswith('bb3') and
                        isinstance(v, str) and re.fullmatch('[A-Za-z0-9_-]{1,100}', v)}
                evidence.append('native_capabilities_after_refusal=' + json.dumps(safe, sort_keys=True))
                raw_writer = device.cat(WRITER, required=False)
                current = Q.preference_values(ET.fromstring(raw_writer)) if raw_writer else {}
                observed = json.loads(current['ticket'].text) if 'ticket' in current else None
                require(observed is None or isinstance(observed, dict), 'diagnosis_ticket_invalid')
                known = getattr(session, 'saved', None)
                known_ticket = known.get('ticket') if isinstance(known, dict) else None
                evidence.append('writer_after_refusal=' + json.dumps({'present':observed is not None,
                    'sameFixture':observed is not None and isinstance(known_ticket, dict) and observed.get('id') == known_ticket.get('id'),
                    'operation':observed.get('operation') if observed and observed.get('operation') in ('CHECK','INSTALL') else None}))
            else:
                evidence.append('native_diagnosis_app_absent=true')
        except Exception:
            evidence.append('native_diagnosis_unavailable=true')
    finally:
        if session:
            try:
                session.cleanup()
            except Exception as error:
                restore_error = error
                evidence.append('FAIL fixture_cleanup_' + safe_error(error))
        try:
            if original is not None:
                traced(device, 'person_restore', lambda: Q.restore_person(device, ET.fromstring(original), evidence, baseline))
                # A private whole-session driver can restore version-migrated typed values
                # while the entire UID is stopped. The comparison below still runs.
                restore_metadata = getattr(device, 'restore_original_metadata_before_comparison', None)
                if restore_metadata is not None:
                    require(callable(restore_metadata), 'final_metadata_restore_unavailable')
                    restore_metadata()
                require(device.installed_hash(PACKAGE) == args.target_sha and device.installed_hash(PACKAGE + '.test') == args.runner_sha, 'restored_installed_candidate_changed')
                before = Q.preference_values(ET.fromstring(original_flutter)); after = Q.preference_values(ET.fromstring(device.cat(FLUTTER)))
                for key in [f'flutter.oc.automation.{owner}', f'flutter.oc.builtinRecovery.{owner}']:
                    require(preference_equal(before.get(key), after.get(key)), 'restored_policy_metadata_changed')
                evidence.append('PASS original_policy_marker_metadata_and_same_installed_app_preserved')
        except Exception as error:
            restore_error = error
    if restore_error:
        evidence.append('FAIL restoration_' + safe_error(restore_error))
    if primary:
        raise primary
    if restore_error:
        raise restore_error


def safe_error(error):
    return str(error) if isinstance(error, Q.Refused) and re.fullmatch('[A-Za-z0-9_]{1,100}', str(error)) else 'host_acceptance_unavailable'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--emulator-go', required=True, action='store_true')
    for option in ['apk', 'runner-apk', 'apksigner', 'aapt', 'out']:
        p.add_argument('--' + option, type=Path, required=True)
    p.add_argument('--target-sha', required=True); p.add_argument('--runner-sha', required=True)
    p.add_argument('--scenario', choices=['cold', 'queue', 'peer', 'fs', 'orphan'], default='cold')
    p.add_argument('--expected-native-failure', choices=['bb9_serialized_checks_failed','bb9_live_peer_failed'])
    p.add_argument('--version', required=True, type=int); p.add_argument('--inherited-emulator-lock-fd', type=int)
    args = p.parse_args(); evidence = []
    try:
        inherited_lock(args.inherited_emulator_lock_fd)
        execute(Device(), args, evidence)
        evidence.append('PASS BB9_locked_install_interrupted_writer_actual_cold_recovery_and_restore' if args.scenario == 'cold' else 'PASS BB9_' + args.scenario + '_locked_regression_and_restore')
        return 0
    except Exception as error:
        evidence.append('FAIL ' + safe_error(error)); return 1
    finally:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text('\n'.join(evidence) + '\n')
        print('\n'.join(evidence), flush=True)


if __name__ == '__main__':
    raise SystemExit(main())
