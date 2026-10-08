"""Mock-only ServiceInstaller checks; all adb/kernel interactions are fake.

Run normally for green checks or with --red for removed-guard controls. Neither
mode establishes Android native commit, cgroup, survival or drainage acceptance.
"""
import copy
import json
from pathlib import Path
import sys
import types
import unittest
from unittest import mock

try:
    import bb9_service_installer as S
    import bb9_external_installer as E
    from test_bb9_external_installer import exported, process, UID, TOKEN, BOOT
except ModuleNotFoundError:
    from tool.qa import bb9_service_installer as S, bb9_external_installer as E
    from tool.qa.test_bb9_external_installer import exported, process, UID, TOKEN, BOOT


class Device:
    serial = 'emulator-5554'

    def __init__(self, fixture):
        self.fixture = fixture
        self.calls = []
        self.fail_action = None
        self.service_present = True
        self.stop_effect = True
        self.actor_exit = True

    def adb(self, *args, **kwargs):
        self.calls.append((args, kwargs))
        if args[:4] == ('shell', 'dumpsys', 'activity', 'services'):
            return types.SimpleNamespace(returncode=0, stdout='ServiceRecord{live}' if self.service_present else '')
        assert args[:2] == ('shell', 'am'), 'No real adb invocation permitted'
        action = args[args.index('-a') + 1]
        if action == self.fail_action:
            raise RuntimeError('synthetic intent failure')
        if action == S.ACTION_PERMIT:
            self.fixture.metadata['state'] = 'running'
        if action == S.ACTION_STOP:
            if self.stop_effect:
                self.fixture.metadata['state'] = 'stopped'
                self.service_present = False
            if self.actor_exit:
                self.fixture.identities.pop(60, None)
        return types.SimpleNamespace(returncode=0, stdout='Starting service')


class Fixture(S.ServiceInstaller):
    def __init__(self):
        super().__init__(Device(self))
        self.identities = {42: process(42, session=0),
                           60: process(60, group=60, session=0),
                           80: process(80, parent=60, group=60, session=0),
                           81: process(81, parent=80),
                           82: process(82, parent=81, group=81, session=81)}
        self.metadata = dict(version=1, ticketId=TOKEN, uid=UID,
                             producer=E.wire_identity(self.identities[60]),
                             root=E.wire_identity(self.identities[80]),
                             leader=E.wire_identity(self.identities[81]), state='waiting')
        self.metadata_raw = None
        self.metadata_missing_reads = 0
        self.uids = {}
        self.nonces = {}
        self.argv = None
        self.cmdlines = {}
        self.cgroups = {}
        self.producer_argv = S.PRODUCER_COMMAND + '\x00'
        self.durable = None
        self.boot = BOOT
        self.signals = []
        self.reads = []

    def _identity(self, pid):
        return copy.deepcopy(self.identities.get(pid))

    def _uid(self, pid):
        return self.uids.get(pid, [UID] * 4)

    def _read(self, pid, name, limit=4096):
        if name == 'cmdline':
            if pid in self.cmdlines:
                return self.cmdlines[pid]
            return self.producer_argv if pid == 60 else '\x00'.join(self.argv or self.export['command']) + '\x00'
        if name == 'environ':
            return self.nonces.get(pid, 'OC_RUNTIME_OWNER=' + TOKEN + '\x00')
        raise AssertionError('Unexpected fake kernel path')

    def _shell(self, script, limit=E.PRIVATE_BYTES):
        self.reads.append((script, limit))
        if script == 'cat /proc/sys/kernel/random/boot_id':
            return self.boot + '\n'
        if script == 'ps -A -o PID,PPID,UID':
            return 'PID PPID UID\n' + '\n'.join(f'{pid} {item["parent"]} {UID}' for pid, item in self.identities.items())
        if script.startswith('head -c 4097 /proc/') and script.endswith('/cgroup'):
            pid = int(script.split('/proc/', 1)[1].split('/')[0])
            return self.cgroups.get(pid, f'0::/uid_{UID}/pid_{pid}\n')
        if script.startswith('p='):
            if self.metadata_missing_reads:
                self.metadata_missing_reads -= 1
                return 'OC-BB9-METADATA-MISSING-1'
            return self.metadata_raw if self.metadata_raw is not None else json.dumps(self.metadata)
        raise AssertionError('Unexpected fake shell operation')

    def _durable(self):
        return copy.deepcopy(self.durable)

    def _signal(self, item, signal):
        self.signals.append((item['pid'], item['startTicks'], signal))
        self.identities.pop(item['pid'], None)

    def ticket(self):
        return dict(version=1, id=TOKEN, rootfsGeneration='b' * 64, targets=['OPENCODE2'], operation='INSTALL',
                    ownership=dict(version=1, boot=BOOT, nonce=TOKEN, generation=1,
                                   root=E.wire_identity(self.root), leader=E.wire_identity(self.leader),
                                   other=[self.export['app'], self.producer]),
                    observed=[E.wire_identity(item) for item in self.witnessed.values()])


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.clock = 0
        self.popen = mock.patch.object(E.subprocess, 'Popen', side_effect=AssertionError('No host child')).start()
        self.addCleanup(mock.patch.stopall)
        mock.patch.object(S.time, 'monotonic', side_effect=lambda: self.clock).start()
        mock.patch.object(S.time, 'sleep', side_effect=self.advance).start()

    def advance(self, seconds):
        self.clock += seconds

    def started(self):
        helper = Fixture()
        answer = helper.start(exported(wrapper=True))
        self.assertEqual(answer['root']['pid'], 80)
        self.assertEqual(answer['leader']['pid'], 81)
        self.assertFalse(helper.permitted)
        self.assertIsNone(helper.process)
        self.popen.assert_not_called()
        return helper

    def committed(self):
        helper = self.started()
        helper.durable = helper.ticket()
        helper.mark_committed(helper.durable)
        return helper

    def test_fixed_start_intent_has_no_export_argv_or_token_extra(self):
        helper = self.started()
        self.assertEqual(helper.device.calls[0][0],
                         ('shell', 'am', 'start-foreground-service', '-n', S.COMPONENT, '-a', S.ACTION_START))
        self.assertTrue(all(timeout['timeout'] <= 2 for _, timeout in helper.device.calls))
        self.assertNotIn(TOKEN, str(helper.device.calls))

    def test_missing_metadata_is_bounded_then_waiting_is_proven(self):
        helper = Fixture()
        helper.metadata_missing_reads = 2
        helper.start(exported())
        self.assertGreaterEqual(self.clock, .2)
        self.assertTrue(helper.root_proven)

    def test_missing_metadata_times_out(self):
        helper = Fixture()
        helper.metadata_missing_reads = 1000
        with self.assertRaisesRegex(E.Refused, 'external_deadline_exceeded'):
            helper.start(exported())
        self.assertLessEqual(self.clock, E.START_SECONDS)

    def test_unknown_metadata_refuses_before_any_permit(self):
        for key, value in [('version', True), ('ticketId', 'c' * 64), ('uid', UID + 1),
                           ('state', 'unknown'), ('error', 'raw details /secret')]:
            helper = Fixture()
            helper.metadata[key] = value
            with self.subTest(key=key), self.assertRaises(E.Refused):
                helper.start(exported())
            self.assertFalse(helper.permitted)

    def test_invalid_json_and_extra_fields_refuse(self):
        for raw in ['{', '[]', json.dumps(dict(Fixture().metadata, unknown='value'))]:
            helper = Fixture()
            helper.metadata_raw = raw
            with self.assertRaisesRegex(E.Refused, 'service_metadata_invalid'):
                helper.start(exported())

    def test_no_native_commit_never_releases_gate(self):
        helper = self.started()
        with self.assertRaisesRegex(E.Refused, 'external_permit_requires_commit'):
            helper.permit()
        self.assertFalse(helper.permitted)
        self.assertEqual(len(helper.device.calls), 1)

    def test_matching_durable_native_commit_permits_once(self):
        helper = self.committed()
        helper.permit()
        self.assertTrue(helper.permitted)
        self.assertEqual(helper.device.calls[-1][0],
                         ('shell', 'am', 'startservice', '-n', S.COMPONENT, '-a', S.ACTION_PERMIT,
                          '--es', 'ticketId', TOKEN))
        with self.assertRaises(E.Refused):
            helper.permit()

    def test_mark_rejects_unpersisted_native_ticket(self):
        helper = self.started()
        with self.assertRaisesRegex(E.Refused, 'external_durable_ticket_mismatch'):
            helper.mark_committed(helper.ticket())
        self.assertIsNone(helper.committed)

    def test_replaced_durable_receipt_refuses_permit(self):
        helper = self.committed()
        helper.durable['id'] = 'c' * 64
        with self.assertRaisesRegex(E.Refused, 'external_durable_ticket_mismatch'):
            helper.permit()
        self.assertFalse(helper.permitted)

    def test_boot_change_refuses_permit(self):
        helper = self.committed()
        helper.boot = '87654321-1234-1234-1234-123456789abc'
        with self.assertRaisesRegex(E.Refused, 'external_boot_changed'):
            helper.permit()
        self.assertFalse(helper.permitted)

    def test_main_app_death_refuses_permit(self):
        helper = self.committed()
        helper.identities.pop(42)
        with self.assertRaisesRegex(E.Refused, 'external_app_changed'):
            helper.permit()
        self.assertFalse(helper.permitted)

    def test_partial_intent_handoff_is_conservatively_permitted(self):
        helper = self.committed()
        helper.device.fail_action = S.ACTION_PERMIT
        with self.assertRaisesRegex(E.Refused, 'service_intent_failed'):
            helper.permit()
        self.assertTrue(helper.permitted)
        with self.assertRaises(E.Refused):
            helper.permit()

    def test_root_pid_reuse_refuses_permit(self):
        helper = self.committed()
        helper.identities[80]['startTicks'] += 1
        with self.assertRaises(E.Refused):
            helper.permit()
        self.assertFalse(helper.permitted)

    def test_producer_pid_reuse_never_gets_reader_exemption(self):
        helper = self.committed()
        helper.identities[60]['startTicks'] += 1
        with self.assertRaisesRegex(E.Refused, 'service_producer_unproven'):
            helper.permit()

    def test_capture_revalidates_exact_producer_reader(self):
        helper = self.started()
        helper.identities[60]['startTicks'] += 1
        with self.assertRaisesRegex(E.Refused, 'service_producer_unproven'):
            helper._capture()

    def test_producer_exact_command_and_uid_required(self):
        for argv, uid in [(S.PRODUCER_COMMAND + '.other\x00', UID),
                          (S.PRODUCER_COMMAND + '\x00arg\x00', UID),
                          (S.PRODUCER_COMMAND + '\x00', UID + 1)]:
            helper = Fixture()
            helper.producer_argv = argv
            helper.uids[60] = [uid] * 4
            with self.assertRaisesRegex(E.Refused, 'service_producer_unproven'):
                helper.start(exported())

    def test_authorized_titles_accept_only_bounded_nul_padding(self):
        for title in [S.PRODUCER_COMMAND, E.PACKAGE]:
            for size in [1, 2, 32, E.PRIVATE_BYTES - len(title)]:
                self.assertTrue(S.android_title_matches(title + '\x00' * size, title))
            for raw in [title, title + '\x00arg\x00', title + '\x00garbage', title + 'other\x00',
                        title + '\x00 \x00', title + '\x00\n', title + '\x00\u00e9\x00',
                        title + '\x00' * (E.PRIVATE_BYTES - len(title) + 1), None, b'title\x00']:
                self.assertFalse(S.android_title_matches(raw, title))
        self.assertFalse(S.android_title_matches(E.PACKAGE + '\x00', S.PRODUCER_COMMAND))
        self.assertFalse(S.android_title_matches(S.PRODUCER_COMMAND + '\x00', E.PACKAGE))
        self.assertFalse(S.android_title_matches('unapproved\x00', 'unapproved'))

    def test_padded_producer_title_is_proven_before_any_permit(self):
        helper = Fixture()
        helper.producer_argv = S.PRODUCER_COMMAND + '\x00' * 32
        answer = helper.start(exported())
        self.assertEqual(answer['root']['pid'], 80)
        self.assertTrue(helper.root_proven)
        self.assertFalse(helper.permitted)
        self.assertEqual(helper.signals, [])
        checks = helper.failure_diagnostics()
        self.assertTrue(checks['producerKernelIdentityMatches'])
        self.assertTrue(checks['producerUidMatches'])
        self.assertFalse(checks['producerTitleExactOneNulMatches'])
        self.assertTrue(checks['producerTitleCanonicalMatches'])

    def test_failed_producer_title_diagnosis_is_only_fixed_booleans(self):
        helper = Fixture()
        helper.producer_argv = S.PRODUCER_COMMAND + '\x00private-argv\x00'
        with self.assertRaisesRegex(E.Refused, 'service_producer_unproven'):
            helper.start(exported())
        checks = helper.failure_diagnostics()
        self.assertTrue(checks['producerKernelIdentityMatches'])
        self.assertTrue(checks['producerUidMatches'])
        self.assertFalse(checks['producerTitleExactOneNulMatches'])
        self.assertFalse(checks['producerTitleCanonicalMatches'])
        self.assertTrue(all(type(value) is bool for value in checks.values()))
        self.assertNotIn('private-argv', json.dumps(checks))
        self.assertNotIn(S.PRODUCER_COMMAND, json.dumps(checks))
        self.assertNotIn(TOKEN, json.dumps(checks))
        self.assertIsNone(helper.producer)
        self.assertFalse(helper.permitted)

    def test_producer_diagnosis_distinguishes_kernel_and_uid_failure(self):
        for change in ['kernel', 'uid']:
            helper = Fixture()
            if change == 'kernel':
                helper.identities[60]['startTicks'] += 1
            else:
                helper.uids[60] = [UID + 1] * 4
            with self.assertRaisesRegex(E.Refused, 'service_producer_unproven'):
                helper.start(exported())
            checks = helper.failure_diagnostics()
            self.assertEqual(checks['producerKernelIdentityMatches'], change != 'kernel')
            self.assertFalse(checks['producerUidMatches'])
            self.assertFalse(checks['producerTitleCanonicalMatches'])
            self.assertTrue(all(type(value) is bool for value in checks.values()))

    def test_protected_root_argv_nonce_and_uid_required(self):
        for change in ['argv', 'nonce', 'uid']:
            helper = Fixture()
            if change == 'argv':
                helper.argv = ['/bin/sleep', '180']
            elif change == 'nonce':
                helper.nonces[80] = 'OC_RUNTIME_OWNER=' + 'c' * 64 + '\x00'
            else:
                helper.uids[80] = [UID + 1] * 4
            with self.subTest(change=change), self.assertRaises(E.Refused):
                helper.start(exported())

    def test_root_must_descend_from_exact_producer(self):
        helper = Fixture()
        helper.identities[80]['parent'] = 1
        helper.metadata['root']['parent'] = 1
        with self.assertRaisesRegex(E.Refused, 'service_root_ancestry_invalid'):
            helper.start(exported())

    def test_leader_requires_own_session_and_nonce(self):
        for change in ['session', 'nonce']:
            helper = Fixture()
            if change == 'session':
                helper.identities[81]['session'] = 0
                helper.metadata['leader']['session'] = 0
            else:
                helper.nonces[81] = ''
            with self.assertRaises(E.Refused):
                helper.start(exported())

    def test_unknown_same_session_peer_remains_refusal(self):
        helper = Fixture()
        helper.identities[90] = process(90, session=0)
        with self.assertRaisesRegex(E.Refused, 'external_child_unproven'):
            helper.start(exported())

    def test_drain_signals_exact_children_root_last_not_android_producer(self):
        helper = self.committed()
        helper.permit()
        helper.drain()
        self.assertEqual([item[0] for item in helper.signals], [81, 82, 80])
        self.assertEqual(set(helper.identities), {42})
        self.assertTrue(helper.drained)
        self.assertEqual(helper.device.calls[-2][0][-3:], ('--es', 'ticketId', TOKEN))
        count = len(helper.device.calls)
        helper.drain()
        self.assertEqual(len(helper.device.calls), count)
        self.assertFalse(any('rm ' in script for script, _ in helper.reads))

    def test_drain_can_follow_main_app_death_without_claiming_uid_quiescence(self):
        helper = self.committed()
        helper.permit()
        helper.identities.pop(42)
        helper.drain()
        self.assertEqual(set(helper.identities), set())
        self.assertTrue(helper.drained)

    def test_drain_reused_pid_signals_nothing(self):
        helper = self.started()
        helper.identities[80]['startTicks'] += 1
        with self.assertRaisesRegex(E.Refused, 'external_pid_reused'):
            helper.drain()
        self.assertFalse(helper.drained)
        self.assertEqual(helper.signals, [])

    def test_stop_requires_metadata_and_dumpsys_absence(self):
        helper = self.started()
        helper.device.stop_effect = False
        with self.assertRaisesRegex(E.Refused, 'external_deadline_exceeded'):
            helper.drain()
        self.assertFalse(helper.drained)
        self.assertLessEqual(self.clock, E.DRAIN_SECONDS)

    def test_terminal_metadata_and_service_absence_do_not_excuse_live_producer(self):
        helper = self.started()
        helper.device.actor_exit = False
        with self.assertRaisesRegex(E.Refused, 'external_deadline_exceeded'):
            helper.drain()
        self.assertFalse(helper.drained)
        self.assertEqual(helper.metadata['state'], 'stopped')
        self.assertFalse(helper.device.service_present)
        self.assertLessEqual(self.clock, E.DRAIN_SECONDS)
        self.assertFalse(any(pid == 60 for pid, _, _ in helper.signals))

    def test_producer_reuse_after_stop_refuses_without_signalling_it(self):
        helper = self.started()
        helper.device.actor_exit = False
        original = helper.device.adb
        def reuse(*args, **kwargs):
            result = original(*args, **kwargs)
            if S.ACTION_STOP in args:
                helper.identities[60]['startTicks'] += 1
            return result
        helper.device.adb = reuse
        with self.assertRaises(E.Refused):
            helper.drain()
        self.assertFalse(helper.drained)
        self.assertFalse(any(pid == 60 for pid, _, _ in helper.signals))

    def test_unreadable_producer_after_stop_refuses_without_signalling_it(self):
        helper = self.started()
        helper.device.actor_exit = False
        original = helper._identity
        def unreadable(pid):
            if pid == 60 and helper.stop_requested:
                raise E.Refused('external_kernel_read_failed')
            return original(pid)
        helper._identity = unreadable
        with self.assertRaises(E.Refused):
            helper.drain()
        self.assertFalse(helper.drained)
        self.assertFalse(any(pid == 60 for pid, _, _ in helper.signals))

    def test_unknown_terminal_metadata_refuses_drain(self):
        helper = self.started()
        original = helper.device.adb
        def bad_stop(*args, **kwargs):
            result = original(*args, **kwargs)
            if S.ACTION_STOP in args:
                helper.metadata['ticketId'] = 'c' * 64
            return result
        helper.device.adb = bad_stop
        with self.assertRaisesRegex(E.Refused, 'service_metadata_invalid'):
            helper.drain()
        self.assertFalse(helper.drained)

    def test_diagnostics_are_fixed_boolean_projection(self):
        helper = self.started()
        helper.metadata['error'] = 'private_probe_error'
        helper._metadata()
        value = helper.failure_diagnostics()
        self.assertTrue(all(type(item) is bool for item in value.values()))
        self.assertNotIn(TOKEN, json.dumps(value))
        self.assertNotIn('private_probe_error', json.dumps(value))

    def test_diagnosis_observes_original_app_loss_without_adopting_a_new_pid(self):
        helper = self.started()
        self.assertTrue(helper.failure_diagnostics()['originalAppIdentityStillLive'])
        helper.identities.pop(42)
        helper.identities[99] = process(99,session=0)
        self.assertFalse(helper.failure_diagnostics()['originalAppIdentityStillLive'])
        self.assertFalse(helper.permitted)
        self.assertEqual(helper.signals,[])
    def test_known_native_error_projects_only_a_fixed_boolean(self):
        helper = self.started()
        helper.metadata['error'] = 'producer_command_invalid'
        helper._metadata()
        self.assertTrue(helper.failure_diagnostics()['serviceError_producer_command_invalid'])
        self.assertTrue(all(type(value) is bool for value in helper.failure_diagnostics().values()))
    def recovered_case(self):
        helper = self.committed()
        helper.permit()
        for pid in [42, 80, 81, 82]:
            helper.identities.pop(pid)
        helper.identities[99] = process(99, session=0)
        helper.cmdlines[99] = E.PACKAGE + '\x00'
        helper.nonces[99] = 'ANDROID_DATA=/data\x00'
        return helper, copy.deepcopy(helper.identities[99])

    def acknowledge(self, helper, identity):
        helper.acknowledge_recovered_app(identity, identity['pid'], identity['startTicks'])

    def test_exact_native_recovered_reader_ack_allows_aux_drain(self):
        helper, identity = self.recovered_case()
        self.acknowledge(helper, identity)
        self.assertEqual(helper.recovered_reader, E.wire_identity(identity))
        self.assertNotIn('recovered_reader', json.dumps(helper.committed))
        helper.drain()
        self.assertTrue(helper.drained)
        self.assertEqual(set(helper.identities), {99})
        self.assertEqual(helper.signals, [])

    def test_padded_recovered_main_title_keeps_exact_native_ack_and_drain(self):
        helper, identity = self.recovered_case()
        helper.cmdlines[99] = E.PACKAGE + '\x00' * 32
        self.acknowledge(helper, identity)
        helper.drain()
        self.assertTrue(helper.drained)
        self.assertEqual(set(helper.identities), {99})
        self.assertEqual(helper.signals, [])

    def test_recovered_app_without_native_ack_remains_unknown_peer(self):
        helper, _ = self.recovered_case()
        with self.assertRaisesRegex(E.Refused, 'external_child_unproven'):
            helper.drain()
        self.assertFalse(helper.drained)

    def test_changed_native_ack_pid_or_ticks_refuses(self):
        for pid, ticks in [(98, 199), (99, 200), (None, None), (False, False), ('99', '199')]:
            helper, identity = self.recovered_case()
            with self.assertRaisesRegex(E.Refused, 'service_reader_native_proof_mismatch'):
                helper.acknowledge_recovered_app(identity, pid, ticks)
            self.assertIsNone(helper.recovered_reader)

    def test_ack_requires_committed_and_permitted(self):
        for phase in ['started', 'committed']:
            helper = getattr(self, phase)()
            with self.assertRaisesRegex(E.Refused, 'service_reader_ack_too_early'):
                self.acknowledge(helper, process(99, session=0))
            self.assertIsNone(helper.recovered_reader)

    def test_ack_requires_old_owned_tree_gone(self):
        helper = self.committed()
        helper.permit()
        with self.assertRaisesRegex(E.Refused, 'service_reader_owned_tree_still_live'):
            self.acknowledge(helper, process(99, session=0))

    def test_ack_refuses_original_app_producer_or_owned_pid(self):
        for pid in [42, 60, 80, 81, 82]:
            helper, _ = self.recovered_case()
            identity = process(pid, session=0)
            with self.assertRaisesRegex(E.Refused, 'service_reader_owned_identity'):
                self.acknowledge(helper, identity)

    def test_ack_refuses_installer_leader_session(self):
        helper, identity = self.recovered_case()
        identity['session'] = helper.leader['session']
        with self.assertRaisesRegex(E.Refused, 'service_reader_owned_identity'):
            self.acknowledge(helper, identity)

    def test_ack_requires_exact_main_cmdline_uid_and_no_owner_nonce(self):
        for change in ['extra_argv', 'suffix', 'uid', 'nonce']:
            helper, identity = self.recovered_case()
            if change == 'extra_argv':
                helper.cmdlines[99] = E.PACKAGE + '\x00extra\x00'
            elif change == 'suffix':
                helper.cmdlines[99] = E.PACKAGE + ':other\x00'
            elif change == 'uid':
                helper.uids[99] = [UID + 1] * 4
            else:
                helper.nonces[99] = 'OC_RUNTIME_OWNER=' + 'c' * 64 + '\x00'
            with self.subTest(change=change), self.assertRaisesRegex(E.Refused, 'service_reader_changed'):
                self.acknowledge(helper, identity)
            self.assertIsNone(helper.recovered_reader)

    def test_ack_rejects_kernel_tick_or_parent_change(self):
        for key in ['startTicks', 'parent']:
            helper, identity = self.recovered_case()
            helper.identities[99][key] += 1
            with self.assertRaisesRegex(E.Refused, 'service_reader_changed'):
                self.acknowledge(helper, identity)
            self.assertIsNone(helper.recovered_reader)

    def test_ack_accepts_strict_wire_identity_as_well_as_parse_stat(self):
        helper, identity = self.recovered_case()
        self.acknowledge(helper, E.wire_identity(identity))
        self.assertEqual(helper.recovered_reader, E.wire_identity(identity))

    def test_ack_rejects_unknown_identity_fields(self):
        helper, identity = self.recovered_case()
        identity['callbackProof'] = True
        with self.assertRaisesRegex(E.Refused, 'service_reader_identity_invalid'):
            self.acknowledge(helper, identity)

    def test_inventory_revalidates_recovered_reader_tick_and_command(self):
        for change in ['tick', 'cmdline', 'nonce', 'uid']:
            helper, identity = self.recovered_case()
            self.acknowledge(helper, identity)
            if change == 'tick':
                helper.identities[99]['startTicks'] += 1
            elif change == 'cmdline':
                helper.cmdlines[99] = E.PACKAGE + '\x00extra\x00'
            elif change == 'nonce':
                helper.nonces[99] = 'OC_RUNTIME_OWNER=' + TOKEN + '\x00'
            else:
                helper.uids[99] = [UID + 1] * 4
            with self.subTest(change=change), self.assertRaisesRegex(E.Refused, 'service_reader_changed'):
                helper._capture()
            self.assertFalse(helper.drained)

    def test_inventory_revalidates_recovered_reader_cgroup(self):
        helper, identity = self.recovered_case()
        self.acknowledge(helper, identity)
        helper.cgroups[99] = f'0::/uid_{UID}/pid_100\n'
        with self.assertRaisesRegex(E.Refused, 'service_reader_cgroup_changed'):
            helper._capture()

    def test_repeat_ack_does_not_refresh_changed_cgroup(self):
        helper, identity = self.recovered_case()
        self.acknowledge(helper, identity)
        helper.cgroups[99] = f'0::/uid_{UID}/pid_100\n'
        with self.assertRaisesRegex(E.Refused, 'service_reader_cgroup_changed'):
            self.acknowledge(helper, identity)

    def test_ack_does_not_admit_another_same_session_android_reader(self):
        helper, identity = self.recovered_case()
        self.acknowledge(helper, identity)
        helper.identities[100] = process(100, session=0)
        helper.cmdlines[100] = E.PACKAGE + '\x00'
        helper.nonces[100] = ''
        with self.assertRaisesRegex(E.Refused, 'external_child_unproven'):
            helper.drain()
        self.assertFalse(helper.drained)

    def test_missing_or_malformed_cgroup_refuses_ack(self):
        for cgroup in ['', 'unsafe', '0::/data\x00bad\n', '0::/' + 'a' * 4096]:
            helper, identity = self.recovered_case()
            helper.cgroups[99] = cgroup
            with self.assertRaisesRegex(E.Refused, 'service_reader_cgroup_invalid'):
                self.acknowledge(helper, identity)
            self.assertIsNone(helper.recovered_reader)

    def native_drain_case(self):
        helper, identity = self.recovered_case()
        self.acknowledge(helper, identity)
        helper.identities.pop(60)
        return helper

    def test_native_drain_requires_both_literal_native_proof_flags(self):
        for value in [False, None, 0, 1, 'true', {}, []]:
            for flags in [(value, True), (True, value)]:
                helper = self.native_drain_case()
                with self.subTest(flags=flags), self.assertRaisesRegex(
                        E.Refused, 'service_native_drain_proof_missing'):
                    helper.acknowledge_native_drain(*flags)
                self.assertFalse(helper.drained)
                self.assertEqual(helper.signals, [])

    def test_native_drain_requires_prior_main_ack_and_permitted_proven_state(self):
        helper, _ = self.recovered_case()
        with self.assertRaisesRegex(E.Refused, 'service_native_drain_too_early'):
            helper.acknowledge_native_drain(True, True)
        for name, value in [('committed', None), ('permitted', False), ('root_proven', False),
                            ('root', None), ('leader', None), ('producer', None)]:
            helper = self.native_drain_case()
            setattr(helper, name, value)
            with self.subTest(name=name), self.assertRaisesRegex(E.Refused, 'service_native_drain_too_early'):
                helper.acknowledge_native_drain(True, True)
            self.assertFalse(helper.drained)

    def test_native_drain_never_adopts_or_signals_fresh_server_inventory(self):
        helper = self.native_drain_case()
        helper.identities[100] = process(100, session=0)
        with self.assertRaisesRegex(E.Refused, 'external_child_unproven'):
            helper._capture()  # The ordinary inventory guard remains strict.
        helper._inventory = mock.Mock(side_effect=AssertionError('No new tree inventory'))
        helper._capture = mock.Mock(side_effect=AssertionError('No new tree capture'))
        helper._metadata = mock.Mock(side_effect=AssertionError('No metadata adoption'))
        before = copy.deepcopy(helper.identities)
        calls = copy.deepcopy(helper.device.calls)
        self.assertIsNone(helper.acknowledge_native_drain(True, True))
        self.assertTrue(helper.drained)
        helper.drain()
        self.assertEqual(helper.identities, before)
        self.assertEqual(helper.device.calls, calls)
        self.assertEqual(helper.signals, [])
        helper._inventory.assert_not_called()
        helper._capture.assert_not_called()
        helper._metadata.assert_not_called()

    def test_native_drain_refuses_every_original_live_identity_including_zombies(self):
        for pid in [80, 81, 82]:
            for state in ['S', 'Z', 'X']:
                helper = self.native_drain_case()
                helper.identities[pid] = dict(helper.witnessed[pid], state=state)
                with self.subTest(pid=pid, state=state), self.assertRaisesRegex(
                        E.Refused, 'service_native_drain_original_live'):
                    helper.acknowledge_native_drain(True, True)
                self.assertFalse(helper.drained)
                self.assertEqual(helper.signals, [])

    def test_native_drain_refuses_reused_original_pid_before_live_guard(self):
        for pid in [80, 81, 82]:
            helper = self.native_drain_case()
            helper.identities[pid] = dict(helper.witnessed[pid], startTicks=9999)
            with self.subTest(pid=pid), self.assertRaisesRegex(E.Refused, 'service_native_drain_original_changed'):
                helper.acknowledge_native_drain(True, True)
            self.assertFalse(helper.drained)
            self.assertEqual(helper.signals, [])

    def test_native_drain_unknown_original_read_refuses_with_fixed_error(self):
        helper = self.native_drain_case()
        original = helper._identity
        def unavailable(pid):
            if pid == 82:
                raise RuntimeError('synthetic private error text')
            return original(pid)
        helper._identity = unavailable
        with self.assertRaisesRegex(E.Refused, '^service_native_drain_original_unproven$'):
            helper.acknowledge_native_drain(True, True)
        self.assertFalse(helper.drained)
        self.assertEqual(helper.signals, [])

    def test_native_drain_retains_root_leader_and_committed_observations(self):
        for pid in [80, 81, 82]:
            helper = self.native_drain_case()
            saved = helper.witnessed.pop(pid)
            helper.identities[pid] = saved
            with self.subTest(pid=pid), self.assertRaisesRegex(E.Refused, 'service_native_drain_original_live'):
                helper.acknowledge_native_drain(True, True)
            self.assertFalse(helper.drained)

    def test_native_drain_producer_live_wait_is_bounded_and_never_signals(self):
        helper = self.native_drain_case()
        helper.identities[60] = process(60, group=60, session=0)
        with self.assertRaisesRegex(E.Refused, 'external_deadline_exceeded'):
            helper.acknowledge_native_drain(True, True)
        self.assertFalse(helper.drained)
        self.assertLessEqual(self.clock, S.PERMIT_SECONDS)
        self.assertEqual(helper.signals, [])

    def test_native_drain_waits_for_exact_producer_disappearance(self):
        helper = self.native_drain_case()
        helper.identities[60] = process(60, group=60, session=0)
        original = self.advance
        def exit_after_delay(seconds):
            original(seconds)
            if self.clock >= .2:
                helper.identities.pop(60, None)
        with mock.patch.object(S.time, 'sleep', side_effect=exit_after_delay):
            helper.acknowledge_native_drain(True, True)
        self.assertTrue(helper.drained)
        self.assertGreaterEqual(self.clock, .2)
        self.assertEqual(helper.signals, [])

    def test_native_drain_refuses_reused_or_unreadable_producer(self):
        for mode in ['reuse', 'unreadable']:
            helper = self.native_drain_case()
            if mode == 'reuse':
                helper.identities[60] = dict(process(60, group=60, session=0), startTicks=9999)
            else:
                original = helper._identity
                def unavailable(pid):
                    if pid == 60:
                        raise RuntimeError('synthetic private error')
                    return original(pid)
                helper._identity = unavailable
            with self.subTest(mode=mode), self.assertRaises(E.Refused):
                helper.acknowledge_native_drain(True, True)
            self.assertFalse(helper.drained)
            self.assertEqual(helper.signals, [])

    def test_native_drain_revalidates_recovered_main_identity_title_uid_nonce_and_cgroup(self):
        for change in ['ticks', 'title', 'uid', 'nonce', 'cgroup']:
            helper = self.native_drain_case()
            if change == 'ticks':
                helper.identities[99]['startTicks'] += 1
            elif change == 'title':
                helper.cmdlines[99] = E.PACKAGE + '\x00extra\x00'
            elif change == 'uid':
                helper.uids[99] = [UID + 1] * 4
            elif change == 'nonce':
                helper.nonces[99] = 'OC_RUNTIME_OWNER=' + TOKEN + '\x00'
            else:
                helper.cgroups[99] = f'0::/uid_{UID}/pid_100\n'
            with self.subTest(change=change), self.assertRaises(E.Refused):
                helper.acknowledge_native_drain(True, True)
            self.assertFalse(helper.drained)

    def test_native_drain_rechecks_original_and_main_after_producer_wait(self):
        for change in ['original_reuse', 'main_reuse']:
            helper = self.native_drain_case()
            helper.identities[60] = process(60, group=60, session=0)
            def exit_and_reuse(seconds):
                self.advance(seconds)
                helper.identities.pop(60, None)
                if change == 'original_reuse':
                    helper.identities[82] = dict(helper.witnessed[82], startTicks=9999)
                else:
                    helper.identities[99]['startTicks'] += 1
            with mock.patch.object(S.time, 'sleep', side_effect=exit_and_reuse):
                with self.subTest(change=change), self.assertRaises(E.Refused):
                    helper.acknowledge_native_drain(True, True)
            self.assertFalse(helper.drained)
            self.assertEqual(helper.signals, [])

    def test_native_drain_refuses_changed_boot(self):
        helper = self.native_drain_case()
        helper.boot = '00000000-0000-0000-0000-000000000001'
        with self.assertRaisesRegex(E.Refused, 'external_boot_changed'):
            helper.acknowledge_native_drain(True, True)
        self.assertFalse(helper.drained)

    def test_native_drain_repeated_ack_rechecks_current_kernel(self):
        helper = self.native_drain_case()
        helper.acknowledge_native_drain(True, True)
        helper.identities[80] = dict(helper.root, startTicks=9999, state='S')
        with self.assertRaisesRegex(E.Refused, 'service_native_drain_original_changed'):
            helper.acknowledge_native_drain(True, True)


RED_PROOFS = [
    ('native_drain_proof_flags', "E.require(native_verified is True and native_owned_writer_gone is True,\n                  'service_native_drain_proof_missing')", 'pass',
     'test_native_drain_requires_both_literal_native_proof_flags'),
    ('native_drain_original_absence', "E.require(current is None, 'service_native_drain_original_live')", 'pass',
     'test_native_drain_refuses_every_original_live_identity_including_zombies'),
    ('native_drain_producer_absence', 'if self._producer_gone():', 'if True:',
     'test_native_drain_producer_live_wait_is_bounded_and_never_signals'),
    ('producer_exit_required', 'and self._producer_gone()', '',
     'test_terminal_metadata_and_service_absence_do_not_excuse_live_producer'),
    ('producer_title_padding', "all(byte == '\\x00' for byte in raw[len(expected):])",
     "len(raw) == len(expected) + 1 and all(byte == '\\x00' for byte in raw[len(expected):])",
     'test_padded_producer_title_is_proven_before_any_permit'),
    ('recovered_main_title_padding', "all(byte == '\\x00' for byte in raw[len(expected):])",
     "len(raw) == len(expected) + 1 and all(byte == '\\x00' for byte in raw[len(expected):])",
     'test_padded_recovered_main_title_keeps_exact_native_ack_and_drain'),
    ('diagnostic_error_allowlist', "error if error in SERVICE_ERRORS else 'unknown' if error is not None else None", 'error',
     'test_diagnostics_are_fixed_boolean_projection'),
    ('commit_required', "E.require(self.committed is not None and not self.permitted, 'external_permit_requires_commit')",
     'pass', 'test_no_native_commit_never_releases_gate'),
    ('durable_permit', "E.require(self._durable() == self.committed, 'external_durable_ticket_mismatch')",
     'pass', 'test_replaced_durable_receipt_refuses_permit'),
    ('boot_permit', "E.require(self._shell('cat /proc/sys/kernel/random/boot_id', 128).strip() ==\n                  self.committed['ownership']['boot'], 'external_boot_changed')",
     'pass', 'test_boot_change_refuses_permit'),
    ('conservative_handoff', 'self.permitted = True\n        self._intent(ACTION_PERMIT, ticket=True)',
     'self._intent(ACTION_PERMIT, ticket=True)\n        self.permitted = True',
     'test_partial_intent_handoff_is_conservatively_permitted'),
    ('producer_exclusion_proof', "self._prove_producer({'producer': self.producer})",
     'pass', 'test_capture_revalidates_exact_producer_reader'),
    ('root_ancestry', "E.require(self.root['parent'] == self.producer['pid'], 'service_root_ancestry_invalid')",
     'pass', 'test_root_must_descend_from_exact_producer'),
    ('root_last', "sorted(live, key=lambda pid: pid == self.root['pid'])", 'sorted(live)',
     'test_drain_signals_exact_children_root_last_not_android_producer'),
    ('service_stop', "value['state'] in {'stopped', 'failed'} and self._service_absent()", 'True',
     'test_stop_requires_metadata_and_dumpsys_absence'),
    ('native_reader_ack', "E.require(type(native_pid) is int and type(native_ticks) is int and\n                  native_pid == expected['pid'] and native_ticks == expected['startTicks'],\n                  'service_reader_native_proof_mismatch')", 'pass',
     'test_changed_native_ack_pid_or_ticks_refuses'),
    ('reader_inventory_recheck', 'self._prove_recovered_reader()\n            rows.pop', 'pass\n            rows.pop',
     'test_inventory_revalidates_recovered_reader_tick_and_command'),
    ('reader_cgroup_recheck', "E.require(self._cgroup(pid) == self.recovered_reader_cgroup, 'service_reader_cgroup_changed')",
     'pass', 'test_inventory_revalidates_recovered_reader_cgroup'),
]


def red_proofs(title_only=False, native_only=False):
    source = Path(S.__file__).read_text()
    original_bases = Fixture.__bases__
    for name, before, after, test_name in RED_PROOFS:
        if title_only and name not in {'producer_title_padding', 'recovered_main_title_padding', 'producer_exit_required'}:
            continue
        if native_only and name not in {'native_drain_proof_flags', 'native_drain_original_absence',
                                        'native_drain_producer_absence'}:
            continue
        assert source.count(before) == 1, name
        mutant = types.ModuleType('bb9_service_installer_mutant')
        exec(compile(source.replace(before, after), S.__file__, 'exec'), mutant.__dict__)
        Fixture.__bases__ = (mutant.ServiceInstaller,)
        try:
            result = unittest.TestResult()
            ServiceTests(test_name).run(result)
            if result.wasSuccessful():
                raise AssertionError('Removed guard did not fail: ' + name)
            if native_only:
                if result.errors:
                    raise AssertionError('Removed native-drain guard caused a test error: ' + name)
                print('PASS removed_guard_failed ' + name +
                      f' tests={result.testsRun} failures={len(result.failures)} errors=0')
            else:
                print('PASS removed_guard_failed ' + name)
        finally:
            Fixture.__bases__ = original_bases
    print('PASS source_restored_without_filesystem_mutation')


if __name__ == '__main__':
    if sys.argv[1:] == ['--native-drain-red']:
        red_proofs(native_only=True)
    elif sys.argv[1:] == ['--title-red']:
        red_proofs(title_only=True)
    elif sys.argv[1:] == ['--red']:
        red_proofs()
    else:
        unittest.main()
