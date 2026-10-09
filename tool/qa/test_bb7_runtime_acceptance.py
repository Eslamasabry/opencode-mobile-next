import copy
import unittest

import bb7_runtime_acceptance as B


class ObservationTest(unittest.TestCase):
    def fixture(self):
        before = dict(boot='old', version=2201, rootfs='a'*64, attempts=0, members=[dict(pid=101, startTicks=20)])
        after = dict(boot='new', version=2201, rootfs='a'*64, attempts=1, healthy=True,
                     foreground=True, activity=False, helper=False, owned=True,
                     old_alive=False, owner_current=True, wanted=True, idle_enabled=False)
        return before, after

    def test_actual_boot_all_predicates_are_required(self):
        before, after = self.fixture()
        self.assertTrue(B.observation_proven(before, after, 'boot', 'wanted'))
        for key, bad in [('boot', 'old'), ('version', 2202), ('rootfs', 'b'*64),
                         ('attempts', 0), ('healthy', False), ('foreground', False),
                         ('activity', True), ('helper', True), ('owned', False), ('owner_current', False)]:
            changed = dict(after, **{key: bad})
            with self.subTest(key=key):
                self.assertFalse(B.observation_proven(before, changed, 'boot', 'wanted'))

    def test_update_requires_increase_same_boot_and_exact_old_tree_drain(self):
        before, after = self.fixture()
        after.update(boot='old', version=2202)
        self.assertTrue(B.observation_proven(before, after, 'update', 'wanted'))
        for key, bad in [('boot', 'new'), ('version', 2201), ('version', 2200), ('old_alive', True)]:
            with self.subTest(key=key, bad=bad):
                self.assertFalse(B.observation_proven(before, dict(after, **{key: bad}), 'update', 'wanted'))

    def test_denials_require_no_runtime_or_attempt_and_stable_rootfs(self):
        before, after = self.fixture()
        after.update(attempts=0, healthy=False, foreground=False, owned=False, wanted=False)
        for mode in ['idle_enabled', 'policy_disabled', 'stopped', 'timeout']:
            mode_after = dict(after, wanted=mode not in ['stopped', 'timeout'], idle_enabled=mode == 'idle_enabled')
            self.assertTrue(B.observation_proven(before, mode_after, 'boot', mode))
            for key, bad in [('attempts', 1), ('healthy', True), ('foreground', True), ('helper', True), ('activity', True), ('rootfs', 'b'*64)]:
                with self.subTest(mode=mode, key=key):
                    self.assertFalse(B.observation_proven(before, dict(mode_after, **{key: bad}), 'boot', mode))

    def test_unknown_events_and_modes_never_pass(self):
        before, after = self.fixture()
        self.assertFalse(B.observation_proven(before, after, 'synthetic_broadcast', 'wanted'))
        self.assertFalse(B.observation_proven(before, after, 'boot', 'unknown'))

class SafetyTest(unittest.TestCase):
    def test_flags_are_fixed_and_conflicts_refuse(self):
        fields = B.parse_fields('INSTRUMENTATION_STATUS: bb7SingleAttempt=true\nINSTRUMENTATION_STATUS: secretCredential=private\n')
        self.assertEqual(fields, {'bb7SingleAttempt': 'true'})
        for raw in ['INSTRUMENTATION_STATUS: bb7SingleAttempt=possibly',
                    'INSTRUMENTATION_STATUS: bb7SingleAttempt=true\nINSTRUMENTATION_RESULT: bb7SingleAttempt=false']:
            with self.assertRaises(B.H.Q.Refused):
                B.parse_fields(raw)

    def test_instrument_rejects_unknown_step_before_device_access(self):
        class Device:
            def app_identity(self):
                self.fail('must not inspect device')
        with self.assertRaises(B.H.Q.Refused):
            B.instrument(Device(), 'arbitraryStep')

    def test_private_state_mode_and_symlink_guard(self):
        import os
        from pathlib import Path
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'state.json'
            B.private_state(path, {'schema': 1, 'private': 'never_print'}, create=True)
            self.assertEqual(B.private_state(path)['private'], 'never_print')
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            with self.assertRaises(FileExistsError):
                B.private_state(path, {'schema': 1}, create=True)
            link = Path(directory) / 'link'
            link.symlink_to(path)
            with self.assertRaises(OSError):
                B.private_state(link)
            os.chmod(path, 0o644)
            with self.assertRaises(B.H.Q.Refused):
                B.private_state(path)

    def test_denial_observes_entire_window_and_never_instruments(self):
        from unittest.mock import patch
        before, after = ObservationTest().fixture()
        after.update(attempts=0, healthy=False, foreground=False, owned=False,
                     wanted=False, idle_enabled=False)
        state = dict(before=before, event='boot', case='stopped', owner='real', original_flutter='unused')
        seconds = [0]
        def sleep(value):
            seconds[0] += value
        with patch.object(B, 'snapshot', return_value=after) as observed, patch.object(B, 'instrument') as instrument:
            self.assertEqual(B.wait_event(None, state, clock=lambda: seconds[0], sleep=sleep), after)
            self.assertEqual(seconds[0], 65)
            self.assertEqual(observed.call_count, 65)
            instrument.assert_not_called()
        seconds[0] = 0
        with patch.object(B, 'snapshot', return_value=dict(after, healthy=True)), patch.object(B, 'instrument') as instrument:
            with self.assertRaises(B.H.Q.Refused):
                B.wait_event(None, state, clock=lambda: seconds[0], sleep=sleep)
            instrument.assert_not_called()

    def test_event_observation_is_persisted_before_native_verify(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        before, after = ObservationTest().fixture()
        state = dict(schema=1, phase='prepared', config={}, before=before, event='boot', case='wanted', owner='real')
        order = []
        class Device:
            def activity_absent(self): return True
            def adb(self, *args, **kwargs):
                if args[:3] == ('shell', 'getprop', 'sys.boot_completed'):
                    return SimpleNamespace(returncode=0, stdout='1')
                if args == ('shell', 'id', '-u'):
                    return SimpleNamespace(returncode=0, stdout='0')
                order.append(args[0]); return SimpleNamespace(returncode=0, stdout='')
        def private(path, value=None, **kwargs):
            if value is None: return state
            order.append(value['phase'])
        def instrument(*args, **kwargs):
            self.assertEqual(kwargs.get('phase'), 'observed')
            self.assertEqual(state['phase'], 'observed')
            order.append('verify')
            return dict(bb7SingleAttempt='true', bb7NoHelperRestored='true', bb7NoActivity='true')
        args = SimpleNamespace(command='reboot', state='unused')
        with patch.object(B, 'private_state', side_effect=private), patch.object(B, 'validate_normal'), \
             patch.object(B, 'require_current'), patch.object(B, 'boot', side_effect=['old', 'new']), \
             patch.object(B, 'wait_event', side_effect=lambda *unused: order.append('read_only') or after), \
             patch.object(B, 'instrument', side_effect=instrument):
            B.execute(Device(), args)
        self.assertLess(order.index('reboot'), order.index('root'))
        self.assertLess(order.index('root'), order.index('read_only'))
        self.assertLess(order.index('read_only'), order.index('observed'))
        self.assertLess(order.index('observed'), order.index('verify'))

    def test_native_budget_boolean_is_invalid(self):
        raw = '<map><boolean name="wanted" value="true"/><boolean name="enabled" value="true"/>' \
              '<string name="oc.builtinRecoveryBudget.real">{"attempts":true}</string></map>'
        with self.assertRaises(B.H.Q.Refused):
            B.native_state(raw, 'real')

class RestoreMetadataTest(unittest.TestCase):
    def test_idle_count_is_never_lowered_and_transitions_are_revoked(self):
        import json
        import xml.etree.ElementTree as ET
        old = dict(version=1, enabled=False, idleMinutes=5, counter=2, generation=0,
                   stopped=False, helperStopped=False, owner=None, helper=None)
        now = dict(old, enabled=True, idleMinutes=60, counter=7, generation=7,
                   stopped=True, helperStopped=True, owner='real', helper='helper')
        def tree(value):
            root = ET.Element('map'); ET.SubElement(root, 'string', name='idleState').text = json.dumps(value)
            return root
        original = tree(old)
        restored = B.restoration_original(original, tree(now))
        value = B.B.idle_state(B.H.Q.preference_values(restored)['idleState'])
        self.assertEqual(value, dict(old, counter=7))
        self.assertEqual(B.B.idle_state(B.H.Q.preference_values(original)['idleState']), old)

    def test_atomic_update_failure_preserves_original_private_state(self):
        from unittest.mock import patch
        from pathlib import Path
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'state.json'
            B.private_state(path, {'schema': 1, 'original': 'preserve'}, create=True)
            with patch.object(B.os, 'replace', side_effect=OSError('simulated')):
                with self.assertRaises(OSError):
                    B.private_state(path, {'schema': 1, 'original': 'replacement'})
            self.assertEqual(B.private_state(path)['original'], 'preserve')


class UiNavigationTest(unittest.TestCase):
    def test_snapshot_is_bounded_and_rejects_untrusted_xml_shapes(self):
        good = '<?xml version="1.0"?><hierarchy><node text="Start"/></hierarchy>UI dump complete'
        self.assertEqual(len(B.ui_nodes(good)), 1)
        for bad in ['', 'x'*262145, '<!DOCTYPE x><hierarchy/>', '<hierarchy/>',
                    '<?xml version="1.0"?><hierarchy>' + '<node/>'*4097 + '</hierarchy>']:
            with self.subTest(bad=bad[:25]), self.assertRaises(B.H.Q.Refused):
                B.ui_nodes(bad)

    def test_switcher_title_line_selects_clickable_ancestor(self):
        raw = '<?xml version="1.0"?><hierarchy><node clickable="true" enabled="true" bounds="[0,0][200,70]">' \
              '<node text="In-app Ubuntu&#10;Connected"/></node></hierarchy>'
        nodes = B.ui_nodes(raw)
        self.assertEqual(B.ui_target(nodes, {'In-app Ubuntu'}, title=True), (100, 35))
        self.assertIsNone(B.ui_target(nodes, {'Stop'}))

    def test_duplicate_or_invalid_action_targets_refuse(self):
        for body in [
            '<node clickable="true" enabled="true" text="Start" bounds="[0,0][50,50]"/>'*2,
            '<node clickable="true" enabled="true" text="Start" bounds="[50,0][0,50]"/>',
        ]:
            raw = '<?xml version="1.0"?><hierarchy>' + body + '</hierarchy>'
            with self.assertRaises(B.H.Q.Refused):
                B.ui_target(B.ui_nodes(raw), {'Start'})

    def test_action_exact_match_never_selects_delete_or_disabled(self):
        raw = '<?xml version="1.0"?><hierarchy>' \
              '<node clickable="true" enabled="true" text="Start deleting data" bounds="[0,0][50,50]"/>' \
              '<node clickable="true" enabled="false" text="Start" bounds="[0,50][50,100]"/>' \
              '</hierarchy>'
        self.assertIsNone(B.ui_target(B.ui_nodes(raw), {'Start'}))


class RealStartJourneyTest(unittest.TestCase):
    def test_healthy_chat_still_navigates_stops_then_starts_in_management(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        def xml(label, point):
            x, y = point
            return '<?xml version="1.0"?><hierarchy><node clickable="true" enabled="true" text="' + label + \
                   '" bounds="[' + str(x-1) + ',' + str(y-1) + '][' + str(x+1) + ',' + str(y+1) + ']"/></hierarchy>'
        external = xml('Cancel', (5, 5)).replace('</hierarchy>',
            '<node text="Open link" clickable="true" enabled="true" bounds="[1,80][20,90]"/>'
            '<node text="Copy link" clickable="true" enabled="true" bounds="[30,80][40,90]"/></hierarchy>')
        screens = [external, xml('In-app Ubuntu&#10;Connected', (10, 10)), xml('Manage servers', (20, 20)),
                   xml('More', (30, 30)).replace('<node clickable=', '<node content-desc="In-app Ubuntu" clickable="true" enabled="true" bounds="[0,0][100,100]"><node clickable=').replace('</hierarchy>', '</node></hierarchy>'), xml('Stop OpenCode on this phone', (40, 40)),
                   xml('Start', (50, 50)), xml('Start', (50, 50)), xml('Connected', (60, 60))]
        class Device:
            index = -1
            taps = []
            def adb(self, *args, **kwargs):
                if args[0] == 'exec-out':
                    self.index += 1
                    return SimpleNamespace(returncode=0, stdout=screens[self.index])
                if args[:3] == ('shell', 'input', 'tap'):
                    self.taps.append(tuple(map(int, args[3:])))
                return SimpleNamespace(returncode=0, stdout='')
            def cat(self, *args): return ''
            def healthy(self): return self.index not in [5, 6]
        device = Device(); evidence = []
        with patch.object(B.time, 'sleep'), patch.object(B.H, 'native_armed', return_value=True), \
             patch.object(B, 'native_state', side_effect=lambda *unused: {'wanted': device.index < 5}), \
             patch.object(B.H.Q, 'connected_open_code_two', return_value=True):
            B.real_start(device, 'real', evidence)
        self.assertEqual(device.taps, [(5, 5), (10, 10), (20, 20), (30, 30), (40, 40), (50, 50)])
        self.assertIn('PASS actual_server_management_Stop_observed', evidence)
        self.assertIn('PASS actual_server_management_Start_authenticated_Connected_OC2', evidence)


class ScopedCardTargetTest(unittest.TestCase):
    def tree(self, own_actions='<node text="More" clickable="true" enabled="true" bounds="[220,100][260,140]"/>'):
        return B.ui_nodes('<?xml version="1.0"?><hierarchy><node bounds="[0,0][300,800]">'
            '<node text="More" clickable="true" enabled="true" bounds="[220,10][260,50]"/>'
            '<node content-desc="In-app Ubuntu&#10;Connected · OpenCode 2 · Running" selected="true" '
            'clickable="true" enabled="true" bounds="[0,80][280,160]">' + own_actions + '</node>'
            '<node content-desc="Other server" clickable="true" enabled="true" bounds="[0,200][280,280]">'
            '<node text="More" clickable="true" enabled="true" bounds="[220,220][260,260]"/>'
            '</node></node></hierarchy>')

    def test_two_cards_more_is_scoped_to_exact_in_app_card(self):
        self.assertEqual(B.ui_card_target(self.tree(), {'In-app Ubuntu'}, {'More'}), (240, 120))

    def test_missing_action_cannot_fall_back_to_other_card(self):
        self.assertIsNone(B.ui_card_target(self.tree(''), {'In-app Ubuntu'}, {'More'}))

    def test_duplicate_action_inside_exact_card_refuses(self):
        actions = '<node text="More" clickable="true" enabled="true" bounds="[220,100][260,140]"/>'*2
        with self.assertRaises(B.H.Q.Refused):
            B.ui_card_target(self.tree(actions), {'In-app Ubuntu'}, {'More'})

    def test_unknown_card_never_returns_another_card_action(self):
        self.assertIsNone(B.ui_card_target(self.tree(), {'Missing owner'}, {'More'}))


class DelayedStopJourneyTest(unittest.TestCase):
    def test_stop_requested_never_reopens_more_while_health_is_draining(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        def xml(label, x):
            return '<?xml version="1.0"?><hierarchy><node text="' + label + \
                '" clickable="true" enabled="true" bounds="[' + str(x-1) + ',0][' + str(x+1) + ',2]"/></hierarchy>'
        def card(label, x):
            return xml(label, x).replace('<hierarchy>', '<hierarchy><node content-desc="In-app Ubuntu" clickable="true" enabled="true">').replace('</hierarchy>', '</node></hierarchy>')
        screens = [xml('In-app Ubuntu', 10), xml('Manage servers', 20), card('More', 30),
                   xml('Stop OpenCode on this phone', 40), card('More', 30),
                   card('Start', 50), card('Start', 50), xml('Connected', 60)]
        class Device:
            index = -1
            taps = []
            def adb(self, *args, **kwargs):
                if args[0] == 'exec-out':
                    self.index += 1
                    return SimpleNamespace(returncode=0, stdout=screens[self.index])
                if args[:3] == ('shell', 'input', 'tap'):
                    self.taps.append(tuple(map(int, args[3:])))
                return SimpleNamespace(returncode=0, stdout='')
            def cat(self, *args): return ''
            def healthy(self): return self.index not in [5, 6]
        device = Device()
        with patch.object(B.time, 'sleep'), patch.object(B.H, 'native_armed', return_value=True), \
             patch.object(B, 'native_state', side_effect=lambda *unused: {'wanted': device.index < 4}), \
             patch.object(B.H.Q, 'connected_open_code_two', return_value=True):
            B.real_start(device, 'real', [])
        self.assertEqual(device.taps, [(10, 1), (20, 1), (30, 1), (40, 1), (50, 1)])


class AuthoredRuntimeTitleTest(unittest.TestCase):
    def locales(self):
        return [{'phoneServerCardTitle': 'In-app Ubuntu', 'phoneSetupOpenPhoneRuntime': '{name} · {runtime}',
                 'setupRuntimeOne': 'OpenCode 1', 'setupRuntimeTwo': 'OpenCode 2'}]

    def test_real_combined_title_scopes_more_to_canonical_runtime(self):
        nodes = ScopedCardTargetTest().tree()
        own = [node for node in nodes if node.get('selected') == 'true'][0]
        own.set('content-desc', 'In-app Ubuntu · OpenCode 2\nConnected · Running')
        titles = B.phone_card_titles(self.locales(), 'openCode2')
        self.assertEqual(B.ui_card_target(nodes, titles, {'More'}), (240, 120))
        self.assertNotIn('In-app Ubuntu · OpenCode 1', titles)
        self.assertNotIn('Copied In-app Ubuntu · OpenCode 2 elsewhere', titles)

    def test_both_runtime_titles_are_generated_only_from_authored_copy(self):
        self.assertEqual(B.phone_card_titles(self.locales(), 'openCode1'),
                         {'In-app Ubuntu', 'In-app Ubuntu · OpenCode 1'})
        self.assertEqual(B.phone_card_titles(self.locales(), 'openCode2'),
                         {'In-app Ubuntu', 'In-app Ubuntu · OpenCode 2'})


class RebootTransportTest(unittest.TestCase):
    def test_reestablishes_root_before_private_observation_without_app_dispatch(self):
        from types import SimpleNamespace
        class Device:
            calls = []
            def adb(self, *args, **kwargs):
                self.calls.append((args, kwargs.get('timeout')))
                return SimpleNamespace(returncode=0, stdout='0\n' if args == ('shell', 'id', '-u') else '')
        device = Device()
        B.ensure_root_transport(device)
        self.assertEqual([call[0] for call in device.calls],
                         [('root',), ('wait-for-device',), ('shell', 'id', '-u')])
        self.assertTrue(all(0 < timeout <= 20 for _, timeout in device.calls))

    def test_non_root_transport_refuses_private_observation(self):
        from types import SimpleNamespace
        class Device:
            def adb(self, *args, **kwargs):
                return SimpleNamespace(returncode=0, stdout='2000\n' if args == ('shell', 'id', '-u') else '')
        with self.assertRaises(B.H.Q.Refused):
            B.ensure_root_transport(Device())


class ObserverThawTest(unittest.TestCase):
    app = dict(pid=123, startTicks=456, state='S')

    def test_thaw_rechecks_exact_identity_and_uses_only_nonsticky_pid(self):
        from types import SimpleNamespace
        calls = []
        class Device:
            def app_identity(inner):
                calls.append('identity'); return dict(self.app)
            def adb(inner, *args, **kwargs):
                calls.append((args, kwargs['timeout']))
                return SimpleNamespace(returncode=0, stdout='', stderr='')
        B.thaw_observer(Device(), self.app, 'bb7RebootVerify', 'observed')
        self.assertEqual(calls, ['identity', (('shell', 'am', 'unfreeze', '123'), 5), 'identity'])

    def test_pre_observation_or_prepare_never_thaws(self):
        class Device:
            def adb(inner, *args, **kwargs): self.fail('must never mutate before observation')
        for step, phase in [('bb7RebootVerify', 'prepared'), ('bb7UpdateVerify', None),
                            ('bb7RebootPrepare', 'observed'), ('bb7Cleanup', 'prepared')]:
            with self.subTest(step=step, phase=phase), self.assertRaises(B.H.Q.Refused):
                B.thaw_observer(Device(), self.app, step, phase)

    def test_pid_reuse_cannot_thaw_another_process(self):
        class Device:
            def app_identity(inner): return dict(self.app, startTicks=789)
            def adb(inner, *args, **kwargs): self.fail('PID reuse must not be signalled')
        with self.assertRaises(B.H.Q.Refused):
            B.thaw_observer(Device(), self.app, 'bb7RebootVerify', 'observed')

    def test_instrument_thaws_before_detachment_and_native_invoke(self):
        from types import SimpleNamespace
        calls = []
        class Device:
            def app_identity(inner): return dict(self.app)
            def wait_detached(inner, app): calls.append('detached')
            def adb(inner, *args, **kwargs):
                calls.append('thaw' if 'unfreeze' in args else 'instrument')
                return SimpleNamespace(returncode=0, stderr='', stdout=
                    'INSTRUMENTATION_STATUS: bb7RebootVerified=true\n'
                    'INSTRUMENTATION_RESULT: builtinRuntimeResult=PASS\nINSTRUMENTATION_CODE: -1')
        B.instrument(Device(), 'bb7RebootVerify', observation=True, phase='observed')
        self.assertEqual(calls, ['thaw', 'detached', 'instrument', 'detached'])


class ExternalSheetCancelTest(unittest.TestCase):
    locales = [{'e7SharedOpenLink': 'Open link', 'externalLinkCopy': 'Copy link', 'agentCardCancelDefault': 'Cancel'}]
    def nodes(self, labels):
        body = ''.join('<node clickable="true" enabled="true" text="' + label + '" bounds="[' + str(i*50) + ',0][' + str(i*50+40) + ',40]"/>' for i, label in enumerate(labels))
        return B.ui_nodes('<?xml version="1.0"?><hierarchy>' + body + '</hierarchy>')

    def test_exact_known_external_sheet_selects_cancel_only(self):
        self.assertEqual(B.external_sheet_cancel(self.nodes(['Open link', 'Copy link', 'Cancel']), self.locales), (120, 20))

    def test_generic_cancel_or_partial_markers_never_cancel(self):
        for labels in [['Cancel'], ['Copy link', 'Cancel'], ['Open link', 'Cancel'],
                       ['Open link maybe', 'Copy link', 'Cancel']]:
            with self.subTest(labels=labels):
                self.assertIsNone(B.external_sheet_cancel(self.nodes(labels), self.locales))

    def test_recognized_sheet_with_ambiguous_cancel_refuses(self):
        with self.assertRaises(B.H.Q.Refused):
            B.external_sheet_cancel(self.nodes(['Open link', 'Copy link', 'Cancel', 'Cancel']), self.locales)


if __name__ == '__main__':
    unittest.main()
