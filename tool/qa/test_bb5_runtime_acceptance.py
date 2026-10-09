import copy
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import xml.etree.ElementTree as ET

import bb5_runtime_acceptance as B
import bb9_component_update_acceptance as H


def node(value):
    item = ET.Element('string', {'name': 'idleState'})
    item.text = json.dumps(value)
    return item


def state(counter=0, **kwargs):
    return dict(version=1, enabled=False, idleMinutes=5, counter=counter, generation=0,
                stopped=False, helperStopped=False, owner=None, helper=None, **kwargs)


class AdapterTest(unittest.TestCase):
    def setUp(self):
        self.session = H.ConcurrentSession
        self.steps = dict(H.STEPS); self.fields = set(H.FIELDS); self.phases = set(H.PHASE_NAMES)
        self.merge = H.Q.merge_person_preferences
        B.configure(H)
        self.device = Mock(); self.evidence = []
        self.device.cat.return_value = None

    def tearDown(self):
        H.ConcurrentSession = self.session; H.STEPS = self.steps; H.FIELDS = self.fields
        H.PHASE_NAMES = self.phases; H.Q.merge_person_preferences = self.merge

    def test_native_idle_fixed_reason_is_not_replaced_by_generic_failure(self):
        device = H.Device(); app = dict(pid=111, startTicks=12, state="S")
        payload = 'INSTRUMENTATION_RESULT: builtinRuntimeResult=FAIL\n' + \
                  'INSTRUMENTATION_RESULT: builtinRuntimeFailure=bb5_cleanup_fixture_invalid\n'
        with patch.object(device, 'app_identity', return_value=app), \
             patch.object(device, 'wait_detached'), \
             patch.object(device, 'adb', return_value=SimpleNamespace(returncode=0,stdout=payload,stderr='')):
            with self.assertRaisesRegex(H.Q.Refused, '^bb5_cleanup_fixture_invalid$'):
                device._instrument('bb5Idle', (), None, None)

    def test_every_native_flag_is_required(self):
        for missing in B.IDLE_FIELDS:
            self.device.instrument.return_value = {key: 'true' for key in B.IDLE_FIELDS - {missing}}
            with self.assertRaises(H.Q.Refused):
                H.ConcurrentSession(self.device, None, self.evidence).run()
            self.assertEqual([], self.evidence)

    def test_real_complete_case_is_labeled_as_stand_in_not_agent_certification(self):
        self.device.instrument.return_value = {key: 'true' for key in B.IDLE_FIELDS}
        H.ConcurrentSession(self.device, None, self.evidence).run()
        self.device.instrument.assert_called_once_with('bb5Idle')
        self.assertIn('stand_in_helper', self.evidence[0])
        self.assertIn('existing_OC2_projects_data_config', self.evidence[-2])
        self.assertTrue(self.evidence[-1].startswith('LIMIT '))

    def test_retained_fixture_requires_exact_cleanup_step_and_absence(self):
        self.device.cat.side_effect = ['present', 'present']
        self.device.instrument.return_value = {'bb5CleanupComplete': 'true'}
        with self.assertRaises(H.Q.Refused):
            H.ConcurrentSession(self.device, None, self.evidence).cleanup()
        self.device.instrument.assert_called_once_with('bb5Cleanup')

    def test_existing_fixture_refuses_before_native_mutation(self):
        self.device.cat.return_value = 'present'
        with self.assertRaises(H.Q.Refused):
            H.ConcurrentSession(self.device, None, self.evidence).run()
        self.device.instrument.assert_not_called()

    def test_bounded_timeout_only_for_real_minute_step(self):
        device = B.Device()
        with patch.object(H.Device, 'adb', return_value=object()) as adb, \
             patch.object(B, 'run_idle_instrumentation', return_value=object()) as idle:
            device.adb('shell', 'am', 'instrument', '-e', 'step', 'bb5Idle', timeout=90)
            self.assertEqual(180, idle.call_args.args[1]); adb.assert_not_called()
            device.adb('shell', 'am', 'instrument', '-e', 'step', 'bb5Cleanup', timeout=90)
            self.assertEqual(90, adb.call_args.kwargs['timeout'])


class NotificationTapTest(unittest.TestCase):
    def xml(self, body=None, package='com.android.systemui', bounds='[20,80][220,180]'):
        root = ET.Element('hierarchy')
        ET.SubElement(root, 'node', {'package': package, 'text': body or next(iter(B.IDLE_BODIES)),
                      'enabled': 'true', 'bounds': bounds})
        return ET.tostring(root, encoding='unicode', xml_declaration=True)

    def test_native_failure_before_notice_keeps_its_fixed_reason(self):
        payload = 'INSTRUMENTATION_RESULT: builtinRuntimeResult=FAIL\n' + \
                  'INSTRUMENTATION_RESULT: builtinRuntimeFailure=bb5_cleanup_fixture_invalid\n'
        process = Mock(); process.finished = True
        returned = SimpleNamespace(returncode=0, stdout=payload, stderr='')
        process.result.return_value = returned
        with patch.object(H, 'inherited_lock'), patch.object(B, '_BoundedProcess', return_value=process):
            try:
                result = B.run_idle_instrumentation(('shell', 'am', 'instrument', '--no-restart', '-e', 'step', 'bb5Idle'), 180)
            except H.Q.Refused:
                self.fail('Native refusal was replaced with a missing-tap error')
        self.assertIs(returned, result)
        process.close.assert_called_once()

    def test_fixed_english_and_arabic_actual_systemui_bounds(self):
        for body in B.IDLE_BODIES:
            self.assertEqual((120, 130), B.idle_notification_bounds(self.xml(body)))

    def test_application_text_or_partial_copy_is_not_tap_proof(self):
        self.assertIsNone(B.idle_notification_bounds(self.xml(package=H.PACKAGE)))
        self.assertIsNone(B.idle_notification_bounds(self.xml(body='Phone server paused')))

    def test_ambiguous_or_malformed_or_oversized_notification_refuses(self):
        duplicate = self.xml().replace('</hierarchy>', self.xml().split('?>', 1)[1]
                                      .removeprefix('<hierarchy>').removesuffix('</hierarchy>') + '</hierarchy>')
        for raw in [duplicate, self.xml(bounds='[220,180][20,80]'), 'x'*(B.UI_BYTES+1),
                    '<?xml version="1.0"?><!DOCTYPE hierarchy><hierarchy></hierarchy>']:
            with self.assertRaises(H.Q.Refused):
                B.idle_notification_bounds(raw)

    def test_real_shade_xml_and_bounds_input_are_required_without_activity_shortcut(self):
        result = SimpleNamespace(returncode=0, stdout=self.xml(), stderr='')
        with patch.object(B, '_bounded_adb', return_value=result) as adb:
            B.tap_idle_notification(B.time.monotonic()+20)
        commands = [call.args[0] for call in adb.call_args_list]
        self.assertEqual([
            ['shell', 'cmd', 'statusbar', 'expand-notifications'],
            ['exec-out', 'uiautomator', 'dump', '--compressed', '/proc/self/fd/1'],
            ['shell', 'input', 'tap', '120', '130']], commands)
        self.assertEqual(B.UI_BYTES, adb.call_args_list[1].kwargs['maximum'])
        self.assertFalse(any('am' in command or 'start' in command for command in commands))

    def test_failed_real_input_cannot_be_reported_as_tap(self):
        results = [SimpleNamespace(returncode=0, stdout=''),
                   SimpleNamespace(returncode=0, stdout=self.xml()), SimpleNamespace(returncode=1, stdout='')]
        with patch.object(B, '_bounded_adb', side_effect=results), self.assertRaises(H.Q.Refused):
            B.tap_idle_notification(B.time.monotonic()+20)

    def process(self, chunks):
        value = SimpleNamespace(stdout=bytearray(), chunks=list(chunks), closed=False)
        class Fake:
            @property
            def stdout(self): return value.stdout
            @property
            def finished(self): return not value.chunks
            def pump(self, timeout): value.stdout.extend(value.chunks.pop(0))
            def result(self, command):
                return SimpleNamespace(returncode=0, stdout=bytes(value.stdout).decode(), stderr='')
            def close(self): value.closed=True
        return Fake(), value

    def invoke(self, process):
        with patch.object(B, '_BoundedProcess', return_value=process), patch.object(H, 'inherited_lock'):
            return B.run_idle_instrumentation(('shell', 'am', 'instrument', '--no-restart', '-w',
                                              '-e', 'step', 'bb5Idle', H.Q.RUNNER), 180)

    def test_partial_marker_waits_for_complete_status_line_and_taps_exactly_once(self):
        fake, observed = self.process([B.NOTIFICATION_MARKER, b'\n', b'INSTRUMENTATION_CODE: -1\n'])
        def tap(deadline):
            self.assertTrue(observed.stdout.endswith(b'\n'))
            self.assertLessEqual(deadline-B.time.monotonic(), 20)
        with patch.object(B, 'tap_idle_notification', side_effect=tap) as tapped:
            result = self.invoke(fake)
        tapped.assert_called_once(); self.assertEqual(0, result.returncode); self.assertTrue(observed.closed)

    def test_missing_ready_marker_cannot_accept_native_pass(self):
        # Removed-fix control: delete the final H.require(tapped, ...) in the
        # production driver. This assertion must fail despite native PASS.
        for marker in [b'', b'INSTRUMENTATION_RESULT: bb5AwaitNotificationTap=true\n',
                       b'prefix '+B.NOTIFICATION_MARKER+b'\n', B.NOTIFICATION_MARKER]:
            fake, observed = self.process([marker, b'\nINSTRUMENTATION_RESULT: builtinRuntimeResult=PASS\n'
                                          if marker != B.NOTIFICATION_MARKER else b''])
            with patch.object(B, 'tap_idle_notification') as tapped, self.assertRaises(H.Q.Refused):
                self.invoke(fake)
            tapped.assert_not_called(); self.assertTrue(observed.closed)

    def test_tap_failure_closes_owned_channel_and_propagates_refusal(self):
        fake, observed = self.process([B.NOTIFICATION_MARKER+b'\n', b'INSTRUMENTATION_CODE: -1\n'])
        with patch.object(B, 'tap_idle_notification', side_effect=H.Q.Refused('bb5_notification_input_failed')):
            with self.assertRaises(H.Q.Refused): self.invoke(fake)
        self.assertTrue(observed.closed)


class IdleCounterRestoreTest(unittest.TestCase):
    def merger(self, original, current):
        # Deliberately model the legacy overwrite; the adapter must override it.
        result = copy.deepcopy(current)
        for key, value in H.Q.preference_values(original).items():
            for child in list(result):
                if child.attrib.get('name') == key:
                    result.remove(child)
            result.append(copy.deepcopy(value))
        return result

    def trees(self, old=2, new=11):
        a = ET.Element('map'); a.append(node(state(old)))
        b = ET.Element('map'); b.append(node(state(new)))
        return a, b

    def test_original_policy_restored_without_rolling_back_counter(self):
        original, current = self.trees()
        result = B.preserve_idle_highwater(original, current, self.merger)
        self.assertEqual(11, B.idle_state(H.Q.preference_values(result)['idleState'])['counter'])
        self.assertEqual(2, B.idle_state(H.Q.preference_values(original)['idleState'])['counter'])

    def test_new_default_idle_metadata_survives_original_absence(self):
        original, current = self.trees()
        original.clear()
        result = B.preserve_idle_highwater(original, current, self.merger)
        self.assertEqual(11, B.idle_state(H.Q.preference_values(result)['idleState'])['counter'])

    def test_counter_regression_or_unfinished_intent_refuses(self):
        original, current = self.trees()
        for value in [state(1), dict(state(11), stopped=True, owner='qa_bb5_idle', generation=11),
                      dict(state(11), enabled=True), dict(state(11), helperStopped=True)]:
            current.clear(); current.append(node(value))
            with self.assertRaises(H.Q.Refused):
                B.preserve_idle_highwater(original, current, self.merger)

    def test_malformed_state_never_silently_resets(self):
        original, current = self.trees()
        for value in [dict(state(11), counter=True), dict(state(11), generation=1.5),
                      dict(state(11), unexpected='field')]:
            current.clear(); current.append(node(value))
            with self.assertRaises(H.Q.Refused):
                B.preserve_idle_highwater(original, current, self.merger)


class MetadataRestoreTest(unittest.TestCase):
    def setUp(self):
        self.original = '<map><string name="flutter.oc.profiles">' + json.dumps([
            {'id': 'owner', 'baseUrl': 'http://127.0.0.1:4097', 'flavor': 'v2'}]) + '</string>' + \
            '<string name="flutter.oc.activeProfile">old_active</string>' + \
            '<string name="flutter.oc.automation.owner">typed_policy</string></map>'
        self.current = self.original.replace('old_active', 'new_active').replace('typed_policy', 'changed') \
            .replace('</map>', '<string name="unrelated">retained</string></map>')
        self.device = Mock()
        def adb(*args, **kwargs):
            if args[:3] == ('shell', 'am', 'force-stop'):
                self.stopped = True
                return SimpleNamespace(returncode=0)
            return SimpleNamespace(returncode=0, stdout='package:' + H.PACKAGE + ' uid:10123\n')
        self.device.adb.side_effect = adb
        self.device.cat.side_effect = lambda path: self.current
        self.stopped = False
        def write(path, raw):
            self.assertTrue(self.stopped)
            self.current = raw
        self.device.write_dead.side_effect = write

    def test_exact_typed_metadata_restored_and_unrelated_keys_retained(self):
        with patch.object(H, 'uid_inventory', return_value={}):
            B.restore_metadata(self.device, self.original, [])
        result = H.Q.preference_values(ET.fromstring(self.current))
        self.assertEqual('old_active', result['flutter.oc.activeProfile'].text)
        self.assertEqual('typed_policy', result['flutter.oc.automation.owner'].text)
        self.assertEqual('retained', result['unrelated'].text)
        self.device.write_dead.assert_called_once()

    def test_full_uid_death_is_required_even_if_main_app_absent(self):
        with patch.object(H, 'uid_inventory', return_value={90: 1}), \
             patch.object(B.time, 'monotonic', side_effect=[0, 0, 11]):
            with self.assertRaises(H.Q.Refused):
                B.restore_metadata(self.device, self.original, [])
        self.device.write_dead.assert_not_called()

    def test_failed_force_stop_does_not_read_or_write_preferences(self):
        self.device.adb.side_effect = None
        self.device.adb.return_value = SimpleNamespace(returncode=1)
        with self.assertRaises(H.Q.Refused):
            B.restore_metadata(self.device, self.original, [])
        self.device.cat.assert_not_called(); self.device.write_dead.assert_not_called()


class NormalArtifactTest(unittest.TestCase):
    def test_older_normal_refuses_before_any_device_command(self):
        device = Mock()
        with self.assertRaises(H.Q.Refused):
            B.validate_normal(device, SimpleNamespace(version=2198, normal_version=2198))
        device.run.assert_not_called()

    def test_wrong_version_refuses_without_any_device_command(self):
        device = Mock()
        with self.assertRaises(H.Q.Refused):
            B.validate_normal(device, SimpleNamespace(version=2200, normal_version=2199))
        device.run.assert_not_called()

    def test_normal_hash_sidecar_and_signer_are_all_checked_before_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'normal.apk'; apk.write_bytes(b'private-test-artifact')
            digest = B.hashlib.sha256(apk.read_bytes()).hexdigest()
            sidecar = Path(directory) / 'normal.sha256'; sidecar.write_text(digest + '  normal.apk\n')
            args = SimpleNamespace(version=2198, normal_version=2199, normal_sha=digest,
                normal_apk=apk, normal_sidecar=sidecar, apksigner=Path('apksigner'), aapt=Path('aapt'))
            device = Mock()
            device.run.side_effect = [SimpleNamespace(returncode=0, stdout='Signer #1 certificate SHA-256 digest: ' + H.Q.CERT),
                SimpleNamespace(returncode=0, stdout="package: name='" + H.PACKAGE + "' versionCode='2199'")]
            try: B.validate_normal(device, args)
            except H.Q.Refused: self.fail('Verified normal2199 unexpectedly refused')
            device.adb.assert_not_called(); device.write_dead.assert_not_called()
            sidecar.write_text('0'*64)
            with self.assertRaises(H.Q.Refused):
                B.validate_normal(device, args)

    def test_normal_restore_install_r_never_downgrades_or_uninstalls(self):
        device = Mock(); device.cat.return_value = None
        device.adb.return_value = SimpleNamespace(returncode=0, stdout='versionCode=2199', stderr='')
        device.installed_hash.return_value = 'a'*64
        args = SimpleNamespace(normal_apk=Path('normal.apk'), normal_sha='a'*64)
        with patch.object(B, 'validate_normal') as validate, patch.object(B, 'restore_metadata') as restore, patch.object(H, 'real_start') as start, \
             patch.object(H, 'selected_profile', return_value='owner'):
            B.restore_normal(device, args, 'private_baseline', [])
        commands = [call.args for call in device.adb.call_args_list]
        self.assertIn(('install', '-r', 'normal.apk'), commands)
        self.assertTrue(all('-d' not in command and 'uninstall' not in command for command in commands))
        validate.assert_called_once(); restore.assert_called_once(); start.assert_called_once()

    def test_storage_failure_preserves_installed_app_data_and_never_claims_restore(self):
        device = Mock(); device.cat.return_value = None
        device.adb.return_value = SimpleNamespace(returncode=1, stdout='INSTALL_FAILED_INSUFFICIENT_STORAGE', stderr='')
        evidence = []
        with patch.object(B, 'validate_normal'), patch.object(B, 'restore_metadata'), patch.object(H, 'real_start') as start:
            with self.assertRaises(H.Q.Refused):
                B.restore_normal(device, SimpleNamespace(normal_apk=Path('normal.apk')), 'private', evidence)
        start.assert_not_called()
        self.assertEqual(['FAIL normal_restore_insufficient_storage_QA_app_data_retained'], evidence)

    def test_changed_normal_artifact_cannot_reach_install_or_metadata_mutation(self):
        device = Mock()
        with patch.object(B, 'validate_normal', side_effect=H.Q.Refused('bb5_normal_hash_mismatch')), \
             patch.object(B, 'restore_metadata') as restore:
            with self.assertRaises(H.Q.Refused):
                B.restore_normal(device, object(), 'private', [])
        restore.assert_not_called(); device.adb.assert_not_called()


class InitialKernelAdmissionTest(unittest.TestCase):
    def fixture(self, other=False, unknown=False, writer=False):
        def identity(pid, parent, group, session):
            return dict(pid=pid, startTicks=pid*100, parent=parent, group=group, session=session)
        app = identity(90, 1, 90, 0); root = identity(100, 90, 90, 0)
        leader = identity(101, 100, 101, 101)
        receipt = dict(version=1, boot='12345678-1234-1234-1234-123456789abc', nonce='a'*64,
                       generation=1, root=root, leader=leader, other=[identity(200, 90, 90, 0)] if other else [])
        prefs = ET.Element('map')
        for key in ['owner', 'restoreOwner']:
            ET.SubElement(prefs, 'string', {'name': key}).text = 'owner'
        for key in ['enabled', 'wanted']:
            ET.SubElement(prefs, 'boolean', {'name': key, 'value': 'true'})
        ET.SubElement(prefs, 'string', {'name': 'oc.builtinRuntimeRecipe.owner'}).text = '{}'
        ET.SubElement(prefs, 'string', {'name': 'oc.builtinRuntimeOwnership.owner'}).text = json.dumps(receipt)
        original = '<map><string name="flutter.oc.profiles">' + json.dumps([
            {'id': 'owner', 'baseUrl': 'http://127.0.0.1:4097', 'flavor': 'v2'}]) + '</string></map>'
        records = [app, root, leader] + ([identity(200, 90, 90, 0)] if unknown else [])
        paths = {H.NATIVE: ET.tostring(prefs, encoding='unicode'),
                 H.WRITER: '<map><string name="ticket">present</string></map>',
                 '/proc/90/cmdline': H.PACKAGE+'\x00\x00',
                 '/proc/100/cmdline': '/data/app/private/lib/x86_64/libproot.so\x00--rootfs='+H.UBUNTU+'\x00',
                 '/proc/101/cmdline': 'opencode2\x00serve\x00',
                 '/proc/101/maps': '1-2 r-xp 0 00:01 2 '+H.UBUNTU+'/opt/opencode2/bin/opencode2\n'}
        for value in records:
            fields = ['S', str(value['parent']), str(value['group']), str(value['session'])] + ['1']*15 + [str(value['startTicks'])]
            paths['/proc/'+str(value['pid'])+'/stat'] = str(value['pid'])+' (oc) '+' '.join(fields)
        device = Mock()
        def adb(*args, **kwargs):
            if args[:3] == ('shell', 'sh', '-c'):
                command = B.shlex.split(args[3])[0]
                path = B.shlex.split(command)[3]
                return SimpleNamespace(returncode=0, stdout=paths[path])
            if args[:3] == ('shell', 'test', '!'):
                return SimpleNamespace(returncode=1 if writer else 0, stdout='')
            if args[:3] == ('shell', 'cmd', 'package'):
                return SimpleNamespace(returncode=0, stdout='package:'+H.PACKAGE+' uid:10123\n')
            if args[:2] == ('shell', 'pidof'):
                return SimpleNamespace(returncode=0, stdout='90\n')
            if args[:2] == ('shell', 'ps'):
                return SimpleNamespace(returncode=0, stdout='PID PPID UID\n'+''.join(
                    str(v['pid'])+' '+str(v['parent'])+' 10123\n' for v in records))
            raise AssertionError('Unexpected command')
        device.adb.side_effect = adb
        return device, original

    def test_existing_current_server_only_is_read_only_setup_admission_not_logical_idle(self):
        device, original = self.fixture()
        evidence = []
        B.inspect_server_only_before_bootstrap(device, original, evidence)
        self.assertEqual(2, len(evidence)); self.assertTrue(evidence[-1].startswith('LIMIT '))
        self.assertTrue(all(c.args[0] != 'install' and 'force-stop' not in c.args for c in device.adb.call_args_list))
        device.write_dead.assert_not_called()

    def test_other_helper_or_unknown_terminal_payload_refuses_without_mutation(self):
        for arguments in [{'other': True}, {'unknown': True}, {'writer': True}]:
            device, original = self.fixture(**arguments)
            with self.assertRaises(H.Q.Refused):
                B.inspect_server_only_before_bootstrap(device, original, [])
            device.write_dead.assert_not_called()
            self.assertTrue(all('force-stop' not in c.args and c.args[0] != 'install' for c in device.adb.call_args_list))

    def test_owned_server_child_with_unknown_executable_is_not_admitted(self):
        device, original = self.fixture()
        base = device.adb.side_effect
        def adb(*args, **kwargs):
            result = base(*args, **kwargs)
            if args[:3] == ('shell', 'sh', '-c') and '/proc/101/maps' in args[3]:
                return SimpleNamespace(returncode=0, stdout='1-2 r-xp 0 00:01 2 /system/bin/sh\n')
            return result
        device.adb.side_effect = adb
        with self.assertRaises(H.Q.Refused):
            B.inspect_server_only_before_bootstrap(device, original, [])
        device.write_dead.assert_not_called()


if __name__ == '__main__':
    unittest.main()


class QaDowngradeAdmissionTest(unittest.TestCase):
    def candidate(self, installed_hash, authorized=True):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'target.apk'; target.write_bytes(b'qa-target')
            runner = Path(directory) / 'runner.apk'; runner.write_bytes(b'qa-runner')
            args = SimpleNamespace(version=2198, normal_version=2199, normal_sha='a'*64,
                qa_normal_downgrade=authorized, apk=target, runner_apk=runner,
                target_sha=B.hashlib.sha256(target.read_bytes()).hexdigest(),
                runner_sha=B.hashlib.sha256(runner.read_bytes()).hexdigest(),
                apksigner=Path('apksigner'), aapt=Path('aapt'))
            device = Mock(); device.installed_hash.return_value = installed_hash
            device.run.side_effect = [
                SimpleNamespace(returncode=0, stdout='Signer #1 certificate SHA-256 digest: '+H.Q.CERT),
                SimpleNamespace(returncode=0, stdout="package: name='"+H.PACKAGE+"' versionCode='2198'"),
                SimpleNamespace(returncode=0, stdout='Signer #1 certificate SHA-256 digest: '+H.Q.CERT),
                SimpleNamespace(returncode=0, stdout="package: name='"+H.PACKAGE+".test' versionCode=''"),
            ]
            device.adb.return_value = SimpleNamespace(returncode=0, stdout='versionCode=2199')
            H.validate_candidates(device, args)
            self.assertEqual([('shell','dumpsys','package',H.PACKAGE)], [c.args for c in device.adb.call_args_list])
    def test_exact_known_normal_permits_private_qa_downgrade(self):
        self.candidate('a'*64)
    def test_other_installed_bytes_refuse_even_when_version_matches(self):
        with self.assertRaises(H.Q.Refused): self.candidate('b'*64)
    def test_version_alone_cannot_authorize_qa_downgrade(self):
        with self.assertRaises(H.Q.Refused): self.candidate('a'*64, authorized=False)
