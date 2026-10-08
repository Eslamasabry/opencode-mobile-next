#!/usr/bin/env python3
"""BB5 private emulator session. Import is inert; whole-session inherited flock is required.

The QA and known normal APK both use versionCode2197. A different version refuses
before mutation rather than requesting a downgrade, uninstall or data clearing.
Initial authorized replacement/force-stop is QA setup, not proof that existing
chats are idle. Native scenario admission proves logical work quiescence after
setup; existing logical-chat idle device qualification remains outside this case.
The canonical OpenCode2 server shares existing projects/data/config; the helper
alone has an empty isolated profile. No agent/session/config mutation is issued.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import shlex
import subprocess
import time
from types import SimpleNamespace
import xml.etree.ElementTree as ET

import bb9_component_update_acceptance as H

IDLE_FIXTURE = H.PRIVATE + '/files/bb5-runtime-qa.json'
IDLE_FIELDS = {'bb5IdlePassed', 'bb5RealIdlePassed', 'bb5ExactDrainPassed',
               'bb5StoppedIntentPassed', 'bb5ForegroundResumePassed',
               'bb5HelperAcknowledgementPassed', 'bb5BudgetPreserved', 'bb5ExplicitStopPassed',
               'bb5IdleNotificationPassed', 'bb5NotificationTapPassed'}
VERSION = 2197
IDLE_BODIES = {
    'Phone server paused while idle. Tap to open OpenCode.',
    'خادم الهاتف متوقف مؤقتًا لعدم وجود نشاط. اضغط لفتح OpenCode.',
}
NOTIFICATION_MARKER = b'INSTRUMENTATION_STATUS: bb5AwaitNotificationTap=true'
UI_BYTES = 262144
INSTRUMENT_BYTES = 131072


class _BoundedProcess:
    """Private stream capture; never retain unbounded native or notification text."""
    def __init__(self, command, maximum):
        self.maximum = maximum
        self.stdout = bytearray(); self.stderr = bytearray()
        self.child = subprocess.Popen(command, stdin=subprocess.DEVNULL,
                                      stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.selector = selectors.DefaultSelector()
        try:
            for pipe, target in [(self.child.stdout, self.stdout), (self.child.stderr, self.stderr)]:
                os.set_blocking(pipe.fileno(), False)
                self.selector.register(pipe, selectors.EVENT_READ, target)
        except BaseException:
            self.close()
            raise

    @property
    def finished(self):
        return not self.selector.get_map() and self.child.poll() is not None

    def pump(self, timeout):
        for key, _ in self.selector.select(timeout):
            try:
                chunk = os.read(key.fileobj.fileno(), 4096)
            except BlockingIOError:
                continue
            if not chunk:
                self.selector.unregister(key.fileobj)
            else:
                key.data.extend(chunk)
                H.require(len(self.stdout) + len(self.stderr) <= self.maximum, 'bb5_private_output_overflow')

    def result(self, command):
        H.require(self.finished, 'bb5_private_process_not_drained')
        return subprocess.CompletedProcess(command, self.child.returncode,
                bytes(self.stdout).decode('utf-8', errors='replace'),
                bytes(self.stderr).decode('utf-8', errors='replace'))

    def close(self):
        try:
            if self.child.poll() is None:
                self.child.terminate()  # Only our captured local adb child, never a process pattern.
                try:
                    self.child.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    self.child.kill()
            self.child.wait(timeout=2)
        finally:
            self.selector.close()
            self.child.stdout.close(); self.child.stderr.close()


def _bounded_adb(arguments, deadline, maximum=16384, cap=5):
    H.require(H.SERIAL == 'emulator-5554', 'bb5_notification_device_invalid')
    remaining = deadline - time.monotonic()
    H.require(remaining > 0, 'bb5_notification_tap_timeout')
    command = ['adb', '-s', H.SERIAL, *arguments]
    process = _BoundedProcess(command, maximum)
    until = min(deadline, time.monotonic() + min(cap, remaining))
    try:
        while not process.finished:
            H.require(time.monotonic() < until, 'bb5_notification_command_timeout')
            process.pump(min(.1, max(0, until-time.monotonic())))
        return process.result(command)
    finally:
        process.close()


def idle_notification_bounds(raw):
    """Only a unique actual SystemUI body qualifies; XML remains in memory."""
    H.require(isinstance(raw, str) and len(raw.encode('utf-8')) <= UI_BYTES and
              '<!DOCTYPE' not in raw and '<!ENTITY' not in raw, 'bb5_notification_xml_invalid')
    begin, end = raw.find('<?xml'), raw.find('</hierarchy>')
    H.require(begin >= 0 and end >= begin, 'bb5_notification_xml_invalid')
    try:
        tree = ET.fromstring(raw[begin:end+len('</hierarchy>')])
    except ET.ParseError:
        raise H.Q.Refused('bb5_notification_xml_invalid') from None
    nodes = list(tree.iter('node'))
    H.require(tree.tag == 'hierarchy' and len(nodes) <= 4096, 'bb5_notification_xml_invalid')
    matches = [node for node in nodes if node.get('package') == 'com.android.systemui' and
               any(value.strip() in IDLE_BODIES for key in ['text', 'content-desc']
                   for value in node.get(key, '').splitlines())]
    H.require(len(matches) <= 1, 'bb5_notification_target_ambiguous')
    if not matches:
        return None
    node = matches[0]
    bounds = re.fullmatch(r'\[(\d{1,5}),(\d{1,5})\]\[(\d{1,5}),(\d{1,5})\]', node.get('bounds', ''))
    H.require(node.get('enabled') == 'true' and bounds is not None, 'bb5_notification_bounds_invalid')
    left, top, right, bottom = map(int, bounds.groups())
    H.require(0 <= left < right <= 10000 and 0 <= top < bottom <= 10000,
              'bb5_notification_bounds_invalid')
    return ((left+right)//2, (top+bottom)//2)


def tap_idle_notification(deadline):
    expanded = _bounded_adb(['shell', 'cmd', 'statusbar', 'expand-notifications'], deadline)
    H.require(expanded.returncode == 0, 'bb5_notification_shade_unavailable')
    while time.monotonic() < deadline:
        # Write to uiautomator's own stdout descriptor, not an on-device XML file.
        result = _bounded_adb(['exec-out', 'uiautomator', 'dump', '--compressed', '/proc/self/fd/1'],
                              deadline, maximum=UI_BYTES, cap=8)
        H.require(result.returncode == 0, 'bb5_notification_ui_unavailable')
        bounds = idle_notification_bounds(result.stdout)
        if bounds is not None:
            tapped = _bounded_adb(['shell', 'input', 'tap', str(bounds[0]), str(bounds[1])], deadline)
            H.require(tapped.returncode == 0, 'bb5_notification_input_failed')
            return
        time.sleep(min(.2, max(0, deadline-time.monotonic())))
    raise H.Q.Refused('bb5_notification_tap_timeout')


def run_idle_instrumentation(arguments, timeout):
    """Keep H.Device's identity/detachment/result guards around the live channel."""
    H.require(H.SERIAL == 'emulator-5554' and 0 < timeout <= 180 and
              arguments[:3] == ('shell', 'am', 'instrument') and
              '--no-restart' in arguments and 'bb5Idle' in arguments,
              'bb5_notification_instrument_invalid')
    H.inherited_lock()
    command = ['adb', '-s', H.SERIAL, *arguments]
    process = _BoundedProcess(command, INSTRUMENT_BYTES)
    deadline = time.monotonic() + timeout
    tapped = False
    try:
        while not process.finished:
            H.require(time.monotonic() < deadline, 'bb5_idle_instrument_timeout')
            process.pump(min(.1, max(0, deadline-time.monotonic())))
            # Complete native STATUS line only, never a substring or final result.
            complete_lines = [line.rstrip(b'\r') for line in bytes(process.stdout).split(b'\n')[:-1]]
            if not tapped and NOTIFICATION_MARKER in complete_lines:
                tap_idle_notification(min(deadline, time.monotonic()+20))
                tapped = True
        H.require(tapped, 'bb5_systemui_notification_tap_not_observed')
        return process.result(command)
    finally:
        process.close()


def inspect_server_only_before_bootstrap(device, original_flutter, evidence):
    """Read-only kernel ownership admission; never infer logical idle from CPU or UI."""
    until = time.monotonic() + 25
    def adb(*args, timeout=2):
        remaining = until - time.monotonic()
        H.require(remaining > 0, 'bb5_initial_runtime_inspection_timeout')
        return device.adb(*args, timeout=min(timeout, remaining))
    def read(path, limit):
        command = 'head -c ' + str(limit + 1) + ' ' + shlex.quote(path) + ' 2>/dev/null'
        result = adb('shell', 'sh', '-c', shlex.quote(command))
        H.require(result.returncode == 0 and len(result.stdout.encode()) <= limit,
                  'bb5_initial_runtime_unreadable')
        return result.stdout
    owner = H.selected_profile(original_flutter)
    original_native = read(H.NATIVE, 524288)
    values = H.Q.preference_values(ET.fromstring(original_native))
    H.require(H.native_armed(original_native, owner), 'bb5_initial_owned_server_required')
    writer_absent = adb('shell', 'test', '!', '-e', H.WRITER)
    H.require(writer_absent.returncode in (0, 1), 'bb5_initial_runtime_unreadable')
    if writer_absent.returncode == 1:
        H.require('ticket' not in H.Q.preference_values(ET.fromstring(read(H.WRITER, 524288))),
                  'bb5_initial_installer_refused')
    receipt = json.loads(values['oc.builtinRuntimeOwnership.' + owner].text)
    H.require(isinstance(receipt, dict) and set(receipt) == {'version', 'boot', 'nonce', 'generation', 'root', 'leader', 'other'} and
              type(receipt['version']) is int and receipt['version'] == 1 and receipt['other'] == [] and
              isinstance(receipt['nonce'], str) and re.fullmatch('[a-f0-9]{64}', receipt['nonce']) and
              isinstance(receipt['boot'], str) and re.fullmatch('[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', receipt['boot']) and
              type(receipt['generation']) is int and 0 < receipt['generation'] <= 2**63-1,
              'bb5_initial_other_runtime_refused')
    root, leader = H.identity(receipt['root']), H.identity(receipt['leader'])
    H.require(root['pid'] != leader['pid'] and leader['pid'] == leader['group'] == leader['session'],
              'bb5_initial_server_receipt_invalid')
    package = adb('shell', 'cmd', 'package', 'list', 'packages', '-U', '--user', '0', H.PACKAGE)
    H.require(package.returncode == 0 and len(package.stdout) <= 4096, 'bb5_initial_uid_unavailable')
    matches = re.findall(r'^package:' + re.escape(H.PACKAGE) + r' uid:(\d+)$', package.stdout, re.M)
    H.require(len(matches) == 1 and int(matches[0]) >= 10000, 'bb5_initial_uid_unavailable')
    rows = H.uid_inventory(SimpleNamespace(adb=adb), int(matches[0]))
    main = adb('shell', 'pidof', H.PACKAGE)
    pids = main.stdout.split()
    H.require(main.returncode == 0 and len(pids) == 1 and pids[0].isdecimal() and int(pids[0]) in rows,
              'bb5_initial_main_unavailable')
    app_pid = int(pids[0])
    identities = {pid: H.Q.parse_stat(read('/proc/' + str(pid) + '/stat', 4096)) for pid in rows}
    H.require(all(item['parent'] == rows[pid] for pid, item in identities.items()) and
              all(H.Q.same_process(item, identities.get(item['pid'])) for item in [root, leader]) and
              app_pid not in {root['pid'], leader['pid']}, 'bb5_initial_runtime_changed')
    tree = {root['pid']}
    for _ in range(len(rows)):
        tree |= {pid for pid, parent in rows.items() if parent in tree}
    H.require(leader['pid'] in tree and set(rows) == tree | {app_pid},
              'bb5_initial_unknown_payload_refused')
    app_title = read('/proc/' + str(app_pid) + '/cmdline', 65536)
    H.require(re.fullmatch(re.escape(H.PACKAGE) + '\x00+', app_title), 'bb5_initial_main_title_invalid')
    private_roots = [H.UBUNTU, '/data/data/' + H.PACKAGE + '/files/linux/ubuntu']
    executables = {base + suffix for base in private_roots for suffix in
                   ['/opt/opencode2/bin/opencode2', '/opt/opencode2']}
    for pid in sorted(tree):
        raw = read('/proc/' + str(pid) + '/cmdline', 65536)
        H.require(raw.endswith('\x00'), 'bb5_initial_payload_argv_invalid')
        argv = raw.rstrip('\x00').split('\x00')
        if pid == root['pid']:
            H.require(H.qualified_payload_root(argv, '', rows[pid]), 'bb5_initial_server_root_unproven')
        else:
            # Only the exact existing OC2 executable qualifies; arbitrary helpers,
            # agent/auth/installers, terminals and server-spawned tools refuse.
            maps = read('/proc/' + str(pid) + '/maps', 1048576)
            H.require(any(len(parts := line.split(None, 5)) == 6 and 'x' in parts[1] and
                          parts[5].removesuffix(' (deleted)') in executables for line in maps.splitlines()),
                      'bb5_initial_non_server_payload_refused')
    H.require(read(H.NATIVE, 524288) == original_native and
              H.uid_inventory(SimpleNamespace(adb=adb), int(matches[0])) == rows and
              all(H.Q.same_process(item, H.Q.parse_stat(read('/proc/' + str(pid) + '/stat', 4096)))
                  for pid, item in identities.items()), 'bb5_initial_runtime_changed')
    evidence += ['PASS initial_full_UID_current_owned_server_only_kernel_inspection',
                 'LIMIT initial_authorized_replacement_stop_is_not_existing_logical_chat_idle_proof']


def idle_state(node):
    H.require(node is not None and node.tag == 'string' and isinstance(node.text, str) and
              len(node.text.encode()) <= 4096, 'bb5_idle_metadata_unavailable')
    value = json.loads(node.text)
    H.require(isinstance(value, dict) and set(value) == {'version', 'enabled', 'idleMinutes', 'counter',
              'generation', 'stopped', 'helperStopped', 'owner', 'helper'} and
              type(value['version']) is int and value['version'] == 1 and
              type(value['enabled']) is bool and type(value['idleMinutes']) is int and
              1 <= value['idleMinutes'] <= 60 and
              all(type(value[k]) is int and 0 <= value[k] <= 2**63-1 for k in ['counter', 'generation']) and
              type(value['stopped']) is bool and type(value['helperStopped']) is bool and
              all(value[k] is None or isinstance(value[k], str) and
                  re.fullmatch('[A-Za-z0-9_-]{1,80}', value[k]) for k in ['owner', 'helper']),
              'bb5_idle_metadata_invalid')
    return value


def preserve_idle_highwater(original, current, merger):
    """The actual native cleanup restores policy and revokes tokens; never reset its counter."""
    now_node = H.Q.preference_values(current).get('idleState')
    before_node = H.Q.preference_values(original).get('idleState')
    if now_node is None:
        H.require(before_node is None, 'bb5_idle_counter_disappeared')
        return merger(original, current)
    now = idle_state(now_node)
    before = idle_state(before_node) if before_node is not None else {'counter': 0, 'enabled': False, 'idleMinutes': 5}
    H.require(now['counter'] >= before['counter'] and now['enabled'] == before['enabled'] and
              now['idleMinutes'] == before['idleMinutes'] and not now['stopped'] and
              not now['helperStopped'] and now['generation'] == 0 and now['owner'] is None and
              now['helper'] is None, 'bb5_idle_cleanup_not_proven')
    merged = merger(original, current)
    for child in list(merged):
        if child.attrib.get('name') == 'idleState':
            merged.remove(child)
    merged.append(copy.deepcopy(now_node))
    return merged


def configure(host=H):
    host.STEPS.update({'bb5Idle': 'bb5IdlePassed', 'bb5Cleanup': 'bb5CleanupComplete'})
    host.FIELDS |= IDLE_FIELDS | {'bb5CleanupComplete', 'bb5AwaitNotificationTap'}
    host.PHASE_NAMES |= {'instrument_' + step + suffix for step in ['bb5Idle', 'bb5Cleanup']
                       for suffix in ['', '_identity_check', '_pre_detach', '_invoke', '_post_detach']}

    class IdleSession:
        def __init__(self, device, args, evidence):
            self.device, self.evidence = device, evidence

        def run(self):
            H.require(self.device.cat(IDLE_FIXTURE, required=False) is None, 'bb5_fixture_already_present')
            fields = self.device.instrument('bb5Idle')
            host.require(all(fields.get(k) == 'true' for k in IDLE_FIELDS), 'bb5_native_idle_scenario_unproven')
            self.evidence += ['PASS actual_minute_background_idle_exact_server_and_stand_in_helper_drain',
                             'PASS actual_foreground_same_generation_resume_helper_ack_budget_and_explicit_Stop',
                             'PASS actual_SystemUI_idle_notification_body_tap_and_native_MainActivity_resume',
                             'SCOPE canonical_server_uses_existing_OC2_projects_data_config_empty_helper_only_isolated',
                             'LIMIT no_real_agent_auth_account_Paseo_readiness_Dart_manual_count_or_cold_idle_marker_certification']

        def cleanup(self):
            if self.device.cat(IDLE_FIXTURE, required=False) is not None:
                self.device.ensure_normal_app()  # Cleanup only, never an observed recovery trigger.
                host.require(self.device.instrument('bb5Cleanup').get('bb5CleanupComplete') == 'true',
                             'bb5_fixture_cleanup_unproven')
            host.require(self.device.cat(IDLE_FIXTURE, required=False) is None and
                         self.device.cat(host.FIXTURE, required=False) is None,
                         'bb5_fixture_cleanup_unproven')
    host.ConcurrentSession = IdleSession
    if not getattr(host.Q.merge_person_preferences, '_bb5_idle_highwater', False):
        base = host.Q.merge_person_preferences
        def merge(original, current):
            return preserve_idle_highwater(original, current, base)
        merge._bb5_idle_highwater = True
        host.Q.merge_person_preferences = merge


class Device(H.Device):
    def adb(self, *args, timeout=8):
        # Only this fixed real-minute native step gets the larger bounded budget.
        if args[:3] == ('shell', 'am', 'instrument') and 'bb5Idle' in args:
            return run_idle_instrumentation(args, 180)
        return super().adb(*args, timeout=timeout)


def validate_normal(device, args):
    H.require(args.version == VERSION and args.normal_version == VERSION, 'bb5_same_version_restore_required')
    H.require(isinstance(args.normal_sha, str) and re.fullmatch('[a-f0-9]{64}', args.normal_sha),
              'bb5_normal_hash_invalid')
    with args.normal_apk.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    H.require(digest == args.normal_sha, 'bb5_normal_hash_mismatch')
    H.require(args.normal_sidecar.stat().st_size <= 4096, 'bb5_normal_sidecar_overflow')
    sidecar = args.normal_sidecar.read_text().strip()
    H.require(re.fullmatch(re.escape(args.normal_sha) + r'(?:[ \t]+\*?[^\r\n]+)?', sidecar),
              'bb5_normal_sidecar_mismatch')
    signature = device.run([str(args.apksigner), 'verify', '--print-certs', str(args.normal_apk)],
                           text=True, timeout=15)
    found = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)', signature.stdout)
    H.require(signature.returncode == 0 and found == [H.Q.CERT], 'bb5_normal_signer_mismatch')
    metadata = device.run([str(args.aapt), 'dump', 'badging', str(args.normal_apk)], text=True, timeout=15)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)'", metadata.stdout)
    H.require(metadata.returncode == 0 and match and match[1] == H.PACKAGE and int(match[2]) == VERSION,
              'bb5_normal_metadata_mismatch')


def restore_metadata(device, original_flutter, evidence):
    """Selective typed metadata only; preserve current profile/auth and unrelated keys."""
    H.require(device.adb('shell', 'am', 'force-stop', H.PACKAGE, timeout=5).returncode == 0,
              'bb5_restore_force_stop_failed')
    until = time.monotonic() + 10
    def bounded_adb(*args, timeout=3):
        remaining = until - time.monotonic()
        H.require(remaining > 0, 'bb5_restore_uid_still_live')
        return device.adb(*args, timeout=min(timeout, remaining))
    bounded = SimpleNamespace(adb=bounded_adb)
    package = bounded_adb('shell', 'cmd', 'package', 'list', 'packages', '-U', '--user', '0', H.PACKAGE)
    H.require(package.returncode == 0 and len(package.stdout) <= 4096, 'bb5_restore_uid_unavailable')
    matches = re.findall(r'^package:' + re.escape(H.PACKAGE) + r' uid:(\d+)$', package.stdout, re.M)
    H.require(len(matches) == 1 and int(matches[0]) >= 10000, 'bb5_restore_uid_unavailable')
    uid = int(matches[0])
    while H.uid_inventory(bounded, uid):
        H.require(time.monotonic() < until, 'bb5_restore_uid_still_live')
        time.sleep(min(.1, max(0, until-time.monotonic())))
    owner = H.selected_profile(original_flutter)
    original = H.Q.preference_values(ET.fromstring(original_flutter))
    desired = {key: original.get(key) for key in ['flutter.oc.activeProfile',
               'flutter.oc.automation.' + owner, 'flutter.oc.builtinRecovery.' + owner]}
    current = ET.fromstring(device.cat(H.FLUTTER))
    H.require(current.tag == 'map', 'bb5_restore_preferences_invalid')
    for key, node in desired.items():
        existing = [child for child in current if child.attrib.get('name') == key]
        H.require(len(existing) <= 1, 'bb5_restore_duplicate_key')
        for child in existing:
            current.remove(child)
        if node is not None:
            current.append(copy.deepcopy(node))
    H.require(not H.uid_inventory(bounded, uid), 'bb5_restore_uid_still_live')
    device.write_dead(H.FLUTTER, ET.tostring(current, encoding='unicode'))
    H.require(not H.uid_inventory(bounded, uid), 'bb5_restore_uid_still_live')
    after = H.Q.preference_values(ET.fromstring(device.cat(H.FLUTTER)))
    H.require(all(H.preference_equal(node, after.get(key)) for key, node in desired.items()),
              'bb5_restore_typed_metadata_changed')
    evidence.append('PASS original_typed_policy_marker_and_active_pointer_restored_entire_UID_dead')


def restore_normal(device, args, original_flutter, evidence):
    validate_normal(device, args)  # Recheck the same artifact immediately before its actual install.
    H.require(device.cat(IDLE_FIXTURE, required=False) is None, 'bb5_normal_restore_fixture_retained')
    restore_metadata(device, original_flutter, evidence)
    result = device.adb('install', '-r', str(args.normal_apk), timeout=120)
    if result.returncode != 0:
        text = result.stdout + result.stderr
        reason = 'insufficient_storage' if 'INSTALL_FAILED_INSUFFICIENT_STORAGE' in text else 'install_refused'
        evidence.append('FAIL normal_restore_' + reason + '_QA_app_data_retained')
        raise H.Q.Refused('bb5_normal_restore_unproven')
    H.require(device.installed_hash(H.PACKAGE) == args.normal_sha, 'bb5_normal_installed_hash_mismatch')
    metadata = device.adb('shell', 'dumpsys', 'package', H.PACKAGE, timeout=5)
    H.require(metadata.returncode == 0 and re.search(r'\bversionCode=2197\b', metadata.stdout),
              'bb5_normal_installed_version_mismatch')
    # Actual normal app/UI proves Connected, not an HTTP response or stale QA boolean.
    H.real_start(device, H.selected_profile(original_flutter), evidence)
    evidence.append('PASS normal_2197_same_signer_install_r_data_preserved_actual_Connected_OC2')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--emulator-go', required=True, action='store_true')
    for option in ['apk', 'runner-apk', 'apksigner', 'aapt', 'out', 'normal-apk', 'normal-sidecar']:
        parser.add_argument('--' + option, type=Path, required=True)
    for option in ['target-sha', 'runner-sha', 'normal-sha']:
        parser.add_argument('--' + option, required=True)
    parser.add_argument('--version', type=int, required=True)
    parser.add_argument('--normal-version', type=int, default=VERSION)
    parser.add_argument('--inherited-emulator-lock-fd', type=int)
    args = parser.parse_args(); args.scenario = 'queue'
    evidence = []; device = None; original_flutter = None; mutation = False; code = 1
    try:
        H.inherited_lock(args.inherited_emulator_lock_fd)
        device = Device()
        validate_normal(device, args)  # All normal artifact gates precede any installation/preferences write.
        H.validate_candidates(device, args)
        original_flutter = device.cat(H.FLUTTER)
        H.selected_profile(original_flutter)
        inspect_server_only_before_bootstrap(device, original_flutter, evidence)
        configure()
        device.restore_original_metadata_before_comparison = lambda: restore_metadata(device, original_flutter, evidence)
        mutation = True
        H.execute(device, args, evidence)
        code = 0
    except Exception as error:
        evidence.append('FAIL ' + H.safe_error(error))
    finally:
        if mutation:
            try:
                restore_normal(device, args, original_flutter, evidence)
            except Exception as error:
                evidence.append('FAIL normal_2197_restoration_' + H.safe_error(error)); code = 1
        if code == 0:
            evidence.append('PASS BB5_locked_actual_idle_stop_resume_and_normal_2197_restoration')
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text('\n'.join(evidence) + '\n')
        print('\n'.join(evidence), flush=True)
    return code


if __name__ == '__main__':
    raise SystemExit(main())
