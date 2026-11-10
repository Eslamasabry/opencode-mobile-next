#!/usr/bin/env python3
"""Private staged BB7 proof; every invocation requires the same inherited emulator lock.

The private mode-0600 state contains original preferences; never publish it.
Actual event observation precedes any verify instrumentation. Import is inert.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import tempfile
import time
from types import SimpleNamespace
import xml.etree.ElementTree as ET

import bb5_runtime_acceptance as B

H = B.H
INITIAL_VERSION, UPDATE_VERSION, NORMAL_VERSION = 2201, 2202, 2202
NORMAL_SHA = B.GENUI_NORMAL_APK_SHA
FIXTURE = H.PRIVATE + '/files/bb7-runtime-qa.json'
CASES = {'wanted', 'idle_enabled', 'policy_disabled', 'stopped', 'timeout'}
STEPS = {'bb7RebootPrepare': 'bb7RebootPrepared', 'bb7RebootVerify': 'bb7RebootVerified',
         'bb7UpdatePrepare': 'bb7UpdatePrepared', 'bb7UpdateVerify': 'bb7UpdateVerified',
         'bb7Cleanup': 'bb7CleanupComplete'}
FLAGS = set(STEPS.values()) | {'bb7ActualBootChanged', 'bb7ActualVersionIncreased',
        'bb7SingleAttempt', 'bb7DeniedNoAttempt', 'bb7NoHelperRestored', 'bb7NoActivity'}


def observation_proven(before, after, event, mode):
    """Pure acceptance predicate, separate from collecting actual kernel/AMS state."""
    if event not in {'boot', 'update'} or mode not in CASES:
        return False
    try:
        if (type(before['version']) is not int or type(after['version']) is not int or
                type(before['attempts']) is not int or type(after['attempts']) is not int or
                not 0 <= before['attempts'] < 3 or
                after['rootfs'] != before['rootfs'] or
                after['owner_current'] is not True or after['activity'] is not False or
                after['helper'] is not False):
            return False
        if event == 'boot':
            if after['boot'] == before['boot'] or after['version'] != before['version']:
                return False
        elif (after['boot'] != before['boot'] or after['version'] <= before['version'] or
              after['old_alive'] is not False):
            return False
        if mode == 'wanted':
            return (after['attempts'] == before['attempts'] + 1 and
                    after['healthy'] is True and after['foreground'] is True and
                    after['owned'] is True and after['wanted'] is True and
                    after['idle_enabled'] is False)
        return (after['attempts'] == before['attempts'] and after['healthy'] is False and
                after['foreground'] is False and after['owned'] is False and
                after['wanted'] is (mode not in {'stopped', 'timeout'}) and
                after['idle_enabled'] is (mode == 'idle_enabled'))
    except (KeyError, TypeError):
        return False


def parse_fields(raw):
    fields = {}
    for line in raw.splitlines():
        match = re.fullmatch(r'INSTRUMENTATION_(?:STATUS|RESULT): ([A-Za-z0-9]+)=([A-Za-z0-9_-]+)', line)
        if match and match[1] in FLAGS | {'builtinRuntimeResult', 'builtinRuntimeFailure'}:
            H.require(match[1] not in fields or fields[match[1]] == match[2], 'bb7_conflicting_result')
            fields[match[1]] = match[2]
    H.require(all(value in {'true', 'false'} for key, value in fields.items() if key in FLAGS), 'bb7_invalid_flag')
    return fields


def private_state(path, value=None, *, create=False):
    """No-follow state file, bounded and owner-only. Never write preference XML to evidence."""
    path = Path(path)
    H.require(path.is_absolute() and path.parent.is_dir() and
              not path.is_relative_to(H.ROOT), 'bb7_private_state_path_required')
    flags = os.O_NOFOLLOW | (os.O_RDWR if value is not None else os.O_RDONLY)
    if create:
        flags |= os.O_CREAT | os.O_EXCL
    fd = os.open(path, flags, 0o600)
    try:
        info = os.fstat(fd)
        H.require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid() and
                  stat.S_IMODE(info.st_mode) == 0o600 and info.st_nlink == 1 and
                  info.st_size <= 3_000_000, 'bb7_private_state_unsafe')
        if value is None:
            data = os.read(fd, 3_000_001)
            result = json.loads(data)
            H.require(isinstance(result, dict) and result.get('schema') == 1, 'bb7_private_state_invalid')
            return result
        data = json.dumps(value, separators=(',', ':')).encode()
        H.require(len(data) <= 3_000_000, 'bb7_private_state_overflow')
        if create:
            with os.fdopen(os.dup(fd), 'wb') as output:
                output.write(data); output.flush(); os.fsync(output.fileno())
        else:
            temporary_fd, temporary_path = tempfile.mkstemp(prefix='.bb7-state-', dir=path.parent)
            try:
                with os.fdopen(temporary_fd, 'wb') as output:
                    output.write(data); output.flush(); os.fsync(output.fileno())
                current = path.lstat()
                H.require((current.st_dev, current.st_ino) == (info.st_dev, info.st_ino), 'bb7_private_state_changed')
                os.replace(temporary_path, path)
                directory_fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
                try:
                    os.fsync(directory_fd)
                finally:
                    os.close(directory_fd)
            finally:
                if os.path.exists(temporary_path):
                    os.unlink(temporary_path)
    finally:
        os.close(fd)


def ui_nodes(raw):
    """Bounded stdout-only UI snapshot; do not retain labels or hierarchy in evidence."""
    H.require(isinstance(raw, str) and len(raw.encode()) <= 262144 and
              '<!DOCTYPE' not in raw and '<!ENTITY' not in raw, 'bb7_ui_snapshot_invalid')
    begin, end = raw.find('<?xml'), raw.rfind('</hierarchy>')
    H.require(begin >= 0 and end >= begin, 'bb7_ui_snapshot_invalid')
    try:
        tree = ET.fromstring(raw[begin:end + len('</hierarchy>')])
    except ET.ParseError:
        raise H.Q.Refused('bb7_ui_snapshot_invalid') from None
    nodes = list(tree.iter('node'))
    H.require(tree.tag == 'hierarchy' and len(nodes) <= 4096, 'bb7_ui_snapshot_invalid')
    return nodes


def ui_target(nodes, labels, *, title=False):
    parents = {child: node for node in nodes for child in node}
    matches = {}
    for node in nodes:
        lines = [line.strip() for key in ['text', 'content-desc'] for line in node.get(key, '').splitlines()]
        matched = any(line in labels or (title and any(
            re.fullmatch(re.escape(label) + r' \+\d{1,3}', line) for label in labels)) for line in lines)
        if not matched or node.get('enabled') == 'false':
            continue
        current = node
        while current is not None and current.get('clickable') != 'true':
            current = parents.get(current)
        if current is None or current.get('enabled') != 'true':
            continue
        bounds = re.fullmatch(r'\[(\d{1,5}),(\d{1,5})\]\[(\d{1,5}),(\d{1,5})\]', current.get('bounds', ''))
        H.require(bounds is not None, 'bb7_ui_bounds_invalid')
        left, top, right, bottom = map(int, bounds.groups())
        H.require(0 <= left < right <= 10000 and 0 <= top < bottom <= 10000, 'bb7_ui_bounds_invalid')
        matches[id(current)] = ((left + right)//2, (top + bottom)//2)
    H.require(len(matches) <= 1, 'bb7_ui_target_ambiguous')
    return next(iter(matches.values()), None)


def ui_card_target(nodes, card_labels, action_labels):
    """The actual Ubuntu clickable card owns its child controls; never climb to the list."""
    parents = {child: node for node in nodes for child in node}
    anchors = {}
    for node in nodes:
        lines = [line.strip() for key in ['text', 'content-desc'] for line in node.get(key, '').splitlines()]
        if not any(line in card_labels for line in lines) or node.get('enabled') == 'false':
            continue
        current = node
        while current is not None and current.get('clickable') != 'true':
            current = parents.get(current)
        if current is not None and current.get('enabled') == 'true':
            anchors[id(current)] = current
    H.require(len(anchors) <= 1, 'bb7_ui_card_ambiguous')
    if not anchors:
        return None
    anchor = next(iter(anchors.values()))
    return ui_target(list(anchor.iter('node')), action_labels)


def phone_card_titles(locales, runtime):
    key = {'openCode1': 'setupRuntimeOne', 'openCode2': 'setupRuntimeTwo'}.get(runtime)
    H.require(key is not None, 'bb7_ui_runtime_invalid')
    result = set()
    for value in locales:
        name = value['phoneServerCardTitle']
        template = value['phoneSetupOpenPhoneRuntime']
        H.require(template.count('{name}') == 1 and template.count('{runtime}') == 1,
                  'bb7_ui_title_template_invalid')
        result.add(name)
        result.add(template.replace('{name}', name).replace('{runtime}', value[key]))
    return result


def external_sheet_cancel(nodes, locales):
    """Cancel only the recognized external-link confirmation; never select Open or Copy."""
    for value in locales:
        opened = ui_target(nodes, {value['e7SharedOpenLink']})
        copied = ui_target(nodes, {value['externalLinkCopy']})
        if opened is not None and copied is not None:
            cancel = ui_target(nodes, {value['agentCardCancelDefault']})
            H.require(cancel is not None, 'bb7_external_sheet_cancel_unavailable')
            return cancel
    return None


def real_start(device, owner, evidence):
    """Navigate to server management and observe a genuine product Start there."""
    H.require(device.adb('shell', 'am', 'start', '-n', H.PACKAGE + '/.MainActivity').returncode == 0,
              'bb7_ui_activity_failed')
    locales = [json.loads((H.ROOT / ('lib/l10n/app_' + language + '.arb')).read_text())
               for language in ['en', 'ar']]
    def labels(*keys):
        return {value[key] for value in locales for key in keys if key in value}
    card_titles = phone_card_titles(locales, 'openCode2')
    def tap(point):
        H.require(device.adb('shell', 'input', 'tap', str(point[0]), str(point[1])).returncode == 0,
                  'bb7_ui_tap_failed')
    management = stopped = started = menu_opened = switcher_opened = stop_requested = False
    cancelled_links = 0
    end = time.monotonic() + 150
    while time.monotonic() < end:
        result = device.adb('exec-out', 'uiautomator', 'dump', '--compressed', '/proc/self/fd/1', timeout=12)
        H.require(result.returncode == 0, 'bb7_ui_unavailable')
        nodes = ui_nodes(result.stdout)
        cancel = external_sheet_cancel(nodes, locales)
        if cancel is not None:
            cancelled_links += 1
            H.require(cancelled_links <= 3, 'bb7_external_sheet_repeated')
            tap(cancel)
            evidence.append('PASS authored_external_link_confirmation_cancelled_no_external_navigation')
            time.sleep(.5)
            continue
        visible = [node.get('text', '') + '\n' + node.get('content-desc', '') for node in nodes]
        if started and device.healthy() and H.native_armed(device.cat(H.NATIVE), owner) and \
                H.Q.connected_open_code_two(visible, labels('serverRowConnected', 'e7WorkspaceConnected')):
            evidence.append('PASS actual_server_management_Start_authenticated_Connected_OC2')
            return
        if not management:
            manage = ui_target(nodes, labels('serverSwitcherManage'))
            if manage:
                tap(manage); management = True
            elif ui_target(nodes, labels('e7SetupAddServer')) is not None:
                # The app may resume the management route from the previous cleanup.
                management = True
            elif not switcher_opened:
                switcher = ui_target(nodes, card_titles | labels('serverSwitcherOpen'), title=True)
                if switcher:
                    tap(switcher); switcher_opened = True
        elif not stopped:
            if stop_requested:
                # Drain is asynchronous. No second menu/action may obscure Start while we wait.
                if not native_state(device.cat(H.NATIVE), owner)['wanted'] and not device.healthy():
                    stopped = True
            else:
                stop = ui_target(nodes, labels('phoneServerCardStopOpenCode'))
                if stop:
                    tap(stop); menu_opened = False; stop_requested = True
                    evidence.append('PASS actual_server_management_Stop_observed')
                elif not native_state(device.cat(H.NATIVE), owner)['wanted'] and not device.healthy():
                    stopped = True
                elif not menu_opened:
                    more = ui_card_target(nodes, card_titles, labels('phoneServerCardMore'))
                    if more:
                        tap(more); menu_opened = True
        elif not started:
            start = ui_target(nodes, labels('phoneServerStart', 'phoneServerStartAndConnect'))
            if start:
                tap(start); started = True
        time.sleep(.5)
    raise H.Q.Refused('bb7_management_start_unproven')


def normal_args(config):
    result = SimpleNamespace(**config)
    for key in ['apk', 'runner_apk', 'normal_apk', 'normal_sidecar', 'apksigner', 'aapt']:
        setattr(result, key, Path(getattr(result, key)))
    # B's version check describes its own QA2202; this adapter validates ONLY normal2202.
    result.version = B.VERSION
    result.normal_version = NORMAL_VERSION
    return result


def validate_normal(device, config):
    H.require(config['normal_sha'] == NORMAL_SHA and
              Path(config['normal_apk']) == Path('/home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk'),
              'bb7_exact_normal_required')
    B.validate_normal(device, normal_args(config))


def installed_version(device):
    result = device.adb('shell', 'dumpsys', 'package', H.PACKAGE)
    codes = re.findall(r'\bversionCode=(\d+)', result.stdout)
    H.require(result.returncode == 0 and codes and len(set(codes)) == 1, 'bb7_version_unavailable')
    return int(codes[0])


def boot(device):
    value = device.cat('/proc/sys/kernel/random/boot_id').strip()
    H.require(re.fullmatch('[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', value), 'bb7_boot_unavailable')
    return value


def prefs(raw):
    H.require(isinstance(raw, str) and len(raw.encode()) <= 1_000_000 and
              '<!DOCTYPE' not in raw and '<!ENTITY' not in raw, 'bb7_preferences_invalid')
    tree = ET.fromstring(raw)
    H.require(tree.tag == 'map', 'bb7_preferences_invalid')
    values = H.Q.preference_values(tree)
    H.require(len(values) == len(tree), 'bb7_duplicate_preference')
    return values


def native_state(raw, owner):
    values = prefs(raw)
    def string(key):
        node = values.get(key)
        return node.text if node is not None and node.tag == 'string' else None
    def boolean(key):
        node = values.get(key)
        H.require(node is not None and node.tag == 'boolean' and node.get('value') in {'true', 'false'},
                  'bb7_boolean_unavailable')
        return node.get('value') == 'true'
    budget = json.loads(string('oc.builtinRecoveryBudget.' + owner) or 'null')
    H.require(isinstance(budget, dict) and type(budget.get('attempts')) is int and
              0 <= budget['attempts'] <= 3, 'bb7_budget_invalid')
    idle = B.idle_state(values['idleState']) if 'idleState' in values else {'enabled': False}
    return dict(owner=string('owner'), wanted=boolean('wanted'), enabled=boolean('enabled'),
                attempts=budget['attempts'], idle_enabled=idle['enabled'],
                receipt=json.loads(string('oc.builtinRuntimeOwnership.' + owner) or 'null'),
                recipe=json.loads(string('oc.builtinRuntimeRecipe.' + owner) or 'null'))


def owner_current(device, owner):
    values = prefs(device.cat(H.FLUTTER))
    node = values.get('flutter.oc.builtinServerOwner')
    return node is not None and node.tag == 'string' and node.text == owner


def snapshot(device, owner, before=None):
    """Read-only. No activity, service dispatch, instrumentation or synthetic broadcast."""
    native = native_state(device.cat(H.NATIVE), owner)
    current_boot = boot(device)
    rootfs = device.cat(H.PRIVATE + '/files/linux/ubuntu.ready').strip()
    H.require(re.fullmatch('[a-f0-9]{64}', rootfs), 'bb7_rootfs_unavailable')
    package = device.adb('shell', 'cmd', 'package', 'list', 'packages', '-U', '--user', '0', H.PACKAGE)
    matches = re.findall(r'^package:' + re.escape(H.PACKAGE) + r' uid:(\d+)$', package.stdout, re.M)
    H.require(package.returncode == 0 and len(matches) == 1, 'bb7_uid_unavailable')
    rows = H.uid_inventory(device, int(matches[0])); app = device.app_identity()
    record = native['receipt']; members = []; owned = False
    if isinstance(record, dict) and record.get('boot') == current_boot and record.get('root') and record.get('leader'):
        root, leader = H.identity(record['root']), H.identity(record['leader'])
        if H.Q.same_process(root, device.identity(root['pid'])) and H.Q.same_process(leader, device.identity(leader['pid'])):
            tree = {root['pid']}
            for _ in rows:
                tree |= {pid for pid, parent in rows.items() if parent in tree}
            members = [device.identity(pid) for pid in tree]
            owned = (record.get('other') == [] and leader['pid'] in tree and all(members) and
                     set(rows) == tree | ({app['pid']} if app else set()))
    helper = set(rows) != {item['pid'] for item in members if item} | ({app['pid']} if app else set())
    return dict(boot=current_boot, version=installed_version(device), rootfs=rootfs,
                attempts=native['attempts'], healthy=device.healthy(), foreground=device.service_state(),
                activity=not device.activity_absent(), helper=helper, owned=owned, members=members,
                old_alive=bool(before and before['boot'] == current_boot and any(
                    H.Q.same_process(item, device.identity(item['pid'])) for item in before['members'])),
                owner_current=native['owner'] == owner and owner_current(device, owner),
                wanted=native['wanted'], idle_enabled=native['idle_enabled'])


def thaw_observer(device, app, step, phase):
    """Wake only the exact cached process for post-observation instrumentation or cleanup."""
    H.require((step in {'bb7RebootVerify', 'bb7UpdateVerify'} and phase == 'observed') or
              (step == 'bb7Cleanup' and phase == 'cleanup'), 'bb7_observer_thaw_phase_invalid')
    H.require(app is not None and H.Q.same_process(app, device.app_identity()),
              'bb7_observer_thaw_identity_changed')
    result = device.adb('shell', 'am', 'unfreeze', str(app['pid']), timeout=5)
    H.require(result.returncode == 0, 'bb7_observer_thaw_failed')
    H.require(H.Q.same_process(app, device.app_identity()), 'bb7_observer_thaw_identity_changed')


def instrument(device, step, mode=None, *, observation=False, phase=None):
    H.require(step in STEPS and (mode is None or mode in CASES), 'bb7_step_invalid')
    if step in {'bb7RebootVerify', 'bb7UpdateVerify', 'bb7Cleanup'}:
        H.require(observation and ((phase == 'observed' and step != 'bb7Cleanup') or
                  (phase == 'cleanup' and step == 'bb7Cleanup')), 'bb7_observer_phase_required')
    else:
        H.require(not observation and phase is None, 'bb7_prepare_thaw_refused')
    app = device.app_identity()
    H.require(app is not None or observation, 'bb7_live_prepare_required')
    if app:
        if observation:
            thaw_observer(device, app, step, phase)
        device.wait_detached(app)
    extras = ['-e', 'case', mode] if mode else []
    result = device.adb('shell', 'am', 'instrument', '--no-restart', '-w', '-e', 'step', step,
                        *extras, H.Q.RUNNER, timeout=90)
    fields = parse_fields(result.stdout)
    reason = fields.get('builtinRuntimeFailure')
    if fields.get('builtinRuntimeResult') == 'FAIL' and isinstance(reason, str) and re.fullmatch('bb7_[a-z_]{1,80}', reason):
        raise H.Q.Refused(reason)
    H.require(result.returncode == 0 and fields.get('builtinRuntimeResult') == 'PASS' and
              fields.get(STEPS[step]) == 'true' and 'INSTRUMENTATION_CODE: -1' in result.stdout,
              'bb7_instrument_failed')
    current = device.app_identity()
    H.require(current and (not app or H.Q.same_process(app, current)), 'bb7_instrument_replaced_app')
    device.wait_detached(current)
    return fields


def ensure_root_transport(device):
    """Emulator reboot resets adbd UID; repair transport only, without dispatching app code."""
    rooted = device.adb('root', timeout=12)
    H.require(rooted.returncode == 0, 'bb7_adb_root_unavailable')
    ready = device.adb('wait-for-device', timeout=20)
    H.require(ready.returncode == 0, 'bb7_adb_root_wait_failed')
    identity = device.adb('shell', 'id', '-u', timeout=5)
    H.require(identity.returncode == 0 and identity.stdout.strip() == '0', 'bb7_adb_root_unconfirmed')


def wait_event(device, saved, *, clock=time.monotonic, sleep=time.sleep):
    before, event, mode = saved['before'], saved['event'], saved['case']
    end = clock() + (100 if mode == 'wanted' else 65)
    last = None
    while clock() < end:
        last = snapshot(device, saved['owner'], before)
        if mode == 'wanted':
            if observation_proven(before, last, event, mode):
                B.inspect_server_only_before_bootstrap(device, saved['original_flutter'], [], normal_sha=NORMAL_SHA)
                # Re-read after expensive exact kernel/payload inspection.
                last = snapshot(device, saved['owner'], before)
                H.require(observation_proven(before, last, event, mode), 'bb7_event_changed')
                return last
        else:
            H.require(observation_proven(before, last, event, mode), 'bb7_denial_not_quiet')
        sleep(min(1, max(0, end-clock())))
    H.require(mode != 'wanted' and last is not None, 'bb7_event_restore_timeout')
    return last


def require_current(device, state):
    H.require(device.installed_hash(H.PACKAGE) == state['current_sha'] and
              installed_version(device) == state['current_version'], 'bb7_installed_candidate_changed')


def restoration_original(original, current):
    """Restore original policy while preserving the monotonic idle counter and revoking tokens."""
    result = copy.deepcopy(original)
    original_values = H.Q.preference_values(result)
    current_values = H.Q.preference_values(current)
    before = B.idle_state(original_values['idleState']) if 'idleState' in original_values else None
    now = B.idle_state(current_values['idleState']) if 'idleState' in current_values else None
    H.require(now is not None or before is None, 'bb7_idle_counter_disappeared')
    if now is not None:
        state = dict(before or now)
        state.update(counter=max((before or {'counter': 0})['counter'], now['counter']),
                     enabled=before['enabled'] if before else False,
                     idleMinutes=before['idleMinutes'] if before else 5,
                     generation=0, stopped=False, helperStopped=False, owner=None, helper=None)
        for node in list(result):
            if node.get('name') == 'idleState':
                result.remove(node)
        ET.SubElement(result, 'string', name='idleState').text = json.dumps(state, separators=(',', ':'))
    return result


def cleanup(device, state, evidence):
    state['phase'] = 'cleanup'
    if device.cat(FIXTURE, required=False) is not None:
        instrument(device, 'bb7Cleanup', observation=True, phase=state['phase'])
    H.require(device.cat(FIXTURE, required=False) is None, 'bb7_fixture_retained')
    # Native merge preserves the maximum spent budget and generation; it cannot reset attempts.
    B.restore_metadata(device, state['original_flutter'], evidence)
    original = restoration_original(ET.fromstring(state['original_native']), device.native())
    merged = H.Q.merge_person_preferences(original, device.native())
    device.write_dead(H.NATIVE, ET.tostring(merged, encoding='unicode'))
    evidence.append('PASS original_native_preferences_and_counter_highwater_restored_while_dead')
    real_start(device, state['owner'], evidence)
    state['phase'] = 'ready'
    for key in ['before', 'event', 'case']:
        state.pop(key, None)


def execute(device, args):
    evidence = []
    if args.command == 'init':
        H.require(args.target_sha and args.runner_sha, 'bb7_candidate_hash_required')
        config = {key: str(getattr(args, key)) for key in
                  ['apk', 'runner_apk', 'normal_apk', 'normal_sidecar', 'apksigner', 'aapt']}
        config.update(target_sha=args.target_sha, runner_sha=args.runner_sha, normal_sha=NORMAL_SHA)
        validate_normal(device, config)
        candidate = normal_args(config); candidate.version = INITIAL_VERSION; candidate.qa_normal_downgrade = True
        H.validate_candidates(device, candidate)
        H.require(device.installed_hash(H.PACKAGE) == NORMAL_SHA and installed_version(device) == NORMAL_VERSION,
                  'bb7_exact_initial_normal_required')
        original_flutter = device.cat(H.FLUTTER); original_native = device.cat(H.NATIVE)
        owner = H.selected_profile(original_flutter)
        B.inspect_server_only_before_bootstrap(device, original_flutter, evidence, normal_sha=NORMAL_SHA)
        state = dict(schema=1, phase='initializing', config=config, owner=owner,
                     original_flutter=original_flutter, original_native=original_native,
                     current_version=INITIAL_VERSION, current_sha=args.target_sha)
        private_state(args.state, state, create=True)
        H.bootstrap(device, candidate, evidence)
        real_start(device, owner, evidence)
        state['phase'] = 'ready'; private_state(args.state, state)
        return evidence + ['PASS initial_QA2201_known_normal_only_downgrade']
    state = private_state(args.state)
    validate_normal(device, state['config'])
    if args.command not in {'restore'}:
        require_current(device, state)
    if args.command == 'prepare':
        H.require(state['phase'] == 'ready' and args.event in {'boot', 'update'} and args.case in CASES,
                  'bb7_prepare_phase_invalid')
        H.require(args.event != 'update' or state['current_version'] == INITIAL_VERSION, 'bb7_update_version_invalid')
        baseline = snapshot(device, state['owner'])
        H.require(baseline['healthy'] and baseline['owned'] and baseline['wanted'] and baseline['attempts'] < 3,
                  'bb7_baseline_invalid')
        state.update(phase='preparing', event=args.event, case=args.case)
        private_state(args.state, state)
        instrument(device, 'bb7RebootPrepare' if args.event == 'boot' else 'bb7UpdatePrepare', args.case)
        state['before'] = snapshot(device, state['owner']); state['phase'] = 'prepared'
        H.require(not state['before']['activity'], 'bb7_activity_not_closed')
        private_state(args.state, state)
        return ['PASS prepared_' + args.event + '_' + args.case]
    if args.command in {'reboot', 'replace'}:
        event = 'boot' if args.command == 'reboot' else 'update'
        H.require(state['phase'] == 'prepared' and state['event'] == event, 'bb7_event_phase_invalid')
        H.require(device.activity_absent(), 'bb7_activity_contaminated')
        if event == 'boot':
            H.require(boot(device) == state['before']['boot'], 'bb7_boot_changed_before_dispatch')
            H.require(device.adb('reboot', timeout=12).returncode == 0, 'bb7_actual_reboot_failed')
            device.adb('wait-for-device', timeout=100)
            end = time.monotonic() + 100
            while time.monotonic() < end:
                result = device.adb('shell', 'getprop', 'sys.boot_completed', timeout=4)
                if result.returncode == 0 and result.stdout.strip() == '1' and boot(device) != state['before']['boot']:
                    break
                time.sleep(1)
            else:
                raise H.Q.Refused('bb7_boot_completion_timeout')
            ensure_root_transport(device)
        else:
            H.require(args.target_sha, 'bb7_update_hash_required')
            config = dict(state['config'], target_sha=args.target_sha)
            candidate = normal_args(config); candidate.version = UPDATE_VERSION; candidate.qa_normal_downgrade = False
            H.validate_candidates(device, candidate)
            H.require(boot(device) == state['before']['boot'], 'bb7_update_boot_changed')
            result = device.adb('install', '-r', str(candidate.apk.resolve()), timeout=120)
            H.require(result.returncode == 0 and 'Success' in result.stdout, 'bb7_actual_update_failed')
            state.update(current_sha=args.target_sha, current_version=UPDATE_VERSION, config=config)
            private_state(args.state, state)
        after = wait_event(device, state)
        # Durable safe proof is recorded BEFORE verify instrumentation can instantiate an app.
        state['observation'] = {key: value for key, value in after.items() if isinstance(value, bool)}
        state['phase'] = 'observed'; private_state(args.state, state)
        fields = instrument(device, 'bb7RebootVerify' if event == 'boot' else 'bb7UpdateVerify', observation=True, phase=state['phase'])
        H.require(fields.get('bb7SingleAttempt' if state['case'] == 'wanted' else 'bb7DeniedNoAttempt') == 'true' and
                  fields.get('bb7NoHelperRestored') == 'true' and fields.get('bb7NoActivity') == 'true',
                  'bb7_native_observation_incomplete')
        state['phase'] = 'verified'; private_state(args.state, state)
        return ['PASS actual_' + event + '_' + state['case'] + '_before_instrumentation',
                'PASS native_verify_no_Activity_no_helper',
                'LIMIT timeout_case_is_persisted_revocation_not_OS_quota_callback' if state['case'] == 'timeout'
                else 'PASS exact_budget_delta_' + ('one' if state['case'] == 'wanted' else 'zero')]
    if args.command == 'cleanup':
        H.require(state['phase'] != 'restored', 'bb7_already_restored')
        cleanup(device, state, evidence); private_state(args.state, state)
        return evidence + ['PASS fixture_removed_original_person_metadata_restored']
    if args.command == 'restore':
        cleanup(device, state, evidence)
        candidate = normal_args(state['config'])
        B.validate_normal(device, candidate)
        B.restore_metadata(device, state['original_flutter'], evidence)
        installed = device.adb('install', '-r', str(candidate.normal_apk), timeout=120)
        H.require(installed.returncode == 0 and 'Success' in installed.stdout and
                  device.installed_hash(H.PACKAGE) == NORMAL_SHA and installed_version(device) == NORMAL_VERSION,
                  'bb7_normal_restore_unproven')
        real_start(device, state['owner'], evidence)
        H.require(device.cat(FIXTURE, required=False) is None and device.installed_hash(H.PACKAGE) == NORMAL_SHA,
                  'bb7_normal_restore_unproven')
        state['phase'] = 'restored'; private_state(args.state, state)
        return evidence + ['PASS normal2202_exact_hash_healthy_Connected_fixture_absent']
    raise H.Q.Refused('bb7_command_invalid')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['init', 'prepare', 'reboot', 'replace', 'cleanup', 'restore'])
    parser.add_argument('--emulator-go', required=True, action='store_true')
    parser.add_argument('--state', type=Path, required=True)
    parser.add_argument('--inherited-emulator-lock-fd', type=int)
    parser.add_argument('--event', choices=['boot', 'update'])
    parser.add_argument('--case', choices=sorted(CASES), default='wanted')
    for key in ['apk', 'runner-apk', 'normal-apk', 'normal-sidecar', 'apksigner', 'aapt']:
        parser.add_argument('--' + key, type=Path)
    parser.add_argument('--target-sha'); parser.add_argument('--runner-sha')
    args = parser.parse_args()
    try:
        H.inherited_lock(args.inherited_emulator_lock_fd)
        if args.command == 'init':
            H.require(all(getattr(args, key) is not None for key in
                          ['apk', 'runner_apk', 'normal_apk', 'normal_sidecar', 'apksigner', 'aapt']), 'bb7_init_paths_required')
        for line in execute(H.Device(), args):
            print(line)
    except Exception as error:
        # Never render exception payloads from subprocesses, preferences or private XML.
        code = str(error) if isinstance(error, H.Q.Refused) and re.fullmatch('[a-z][a-z0-9_]{0,100}', str(error)) else 'bb7_unavailable'
        print('FAIL ' + code)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
