"""AST-extracted private restoration mocks; no session/device code executes."""
import ast
import copy
import io
from pathlib import Path
import re
import sys
from types import SimpleNamespace
import unittest
from unittest import mock
import xml.etree.ElementTree as ET

SOURCE = Path(__file__).resolve().parents[2] / 'docs/qa/BB9-2026-10-07/service-normal-restore-session.py'
PACKAGE = 'io.github.eslamasabry.opencode_mobile'
FLUTTER = 'fixed-private-preferences'
KEYS = ['flutter.oc.automation.owner', 'flutter.oc.builtinRecovery.owner']


class Refused(Exception):
    pass


def require(value, code):
    if not value:
        raise Refused(code)


def values(tree):
    return {node.attrib['name']: node for node in tree if 'name' in node.attrib}


def equal(before, after):
    def typed(node):
        return None if node is None else (node.tag, tuple(sorted(node.attrib.items())), node.text)
    return typed(before) == typed(after)


class FakeDevice:
    def __init__(self):
        self.events = []
        self.inventory = []
        self.default_inventory = {}
        self.force_stop_code = 0
        self.package_code = 0
        self.package_stdout = 'package:' + PACKAGE + ' uid:10234\n'
        self.xml = ('<map><string name="flutter.oc.activeProfile">temporary</string>'
                    '<string name="' + KEYS[0] + '">temporary</string>'
                    '<boolean name="' + KEYS[1] + '" value="false" />'
                    '<string name="flutter.oc.profiles">retained-profile-data</string>'
                    '<string name="flutter.oc.auth.owner">retained-auth-data</string>'
                    '<long name="flutter.qa.additive" value="4" /></map>')
        self.writes = []
        self.ignore_write = False
        self.cat_hook = None

    def adb(self, *args, timeout):
        self.events.append((args, timeout))
        if args == ('shell', 'am', 'force-stop', PACKAGE):
            return SimpleNamespace(returncode=self.force_stop_code, stdout='')
        if args == ('shell', 'cmd', 'package', 'list', 'packages', '-U', '--user', '0', PACKAGE):
            return SimpleNamespace(returncode=self.package_code, stdout=self.package_stdout)
        if args == ('mock-inventory',):
            return self.inventory.pop(0) if self.inventory else self.default_inventory
        raise AssertionError('No actual adb call')

    def cat(self, path):
        self.events.append(('cat', path))
        if self.cat_hook:
            self.cat_hook()
        assert path == FLUTTER
        return self.xml

    def write_dead(self, path, raw):
        self.events.append(('write', path))
        self.writes.append((path, raw))
        if not self.ignore_write:
            self.xml = raw


class MetadataTests(unittest.TestCase):
    source = SOURCE.read_text()

    def setUp(self):
        self.clock = 0
        self.lines = []
        self.pointer = ET.fromstring('<string name="flutter.oc.activeProfile">owner</string>')
        self.policy = {
            KEYS[0]: ET.fromstring('<boolean name="' + KEYS[0] + '" value="true" />'),
            KEYS[1]: ET.fromstring('<string name="' + KEYS[1] + '">original-marker</string>')}
        self.device = FakeDevice()

    def advance(self, seconds):
        self.clock += seconds

    def call(self):
        def inventory(device, uid):
            self.assertEqual(uid, 10234)
            return device.adb('mock-inventory', timeout=3)
        h = SimpleNamespace(PACKAGE=PACKAGE, FLUTTER=FLUTTER, require=require,
                            uid_inventory=inventory, preference_equal=equal,
                            Q=SimpleNamespace(preference_values=values))
        namespace = dict(H=h, ET=ET, re=re, types=SimpleNamespace(SimpleNamespace=SimpleNamespace),
                         time=SimpleNamespace(monotonic=lambda: self.clock, sleep=self.advance),
                         original_active=self.pointer, original_policy=self.policy,
                         original_policy_keys=KEYS, copy=copy, lines=self.lines)
        node = next(node for node in ast.parse(self.source).body
                    if isinstance(node, ast.FunctionDef) and node.name == 'restore_original_metadata')
        exec(compile(ast.Module(body=[node], type_ignores=[]), 'metadata-restore-mock', 'exec'), namespace)
        with mock.patch('builtins.print') as output:
            namespace['restore_original_metadata'](self.device)
        return output

    def test_exact_typed_restoration_and_pointer_still_get_compared(self):
        output = self.call()
        restored = values(ET.fromstring(self.device.xml))
        self.assertTrue(equal(self.pointer, restored.get('flutter.oc.activeProfile')))
        for key, original in self.policy.items():
            self.assertTrue(equal(original, restored.get(key)))
        self.assertEqual(len(self.device.writes), 1)
        self.assertEqual(len(self.lines), 2)
        self.assertTrue(all(line.startswith('PASS ') for line in self.lines))
        self.assertEqual(output.call_count, 2)

    def test_unrelated_current_profile_auth_and_additive_keys_are_retained(self):
        before = values(ET.fromstring(self.device.xml))
        self.call()
        after = values(ET.fromstring(self.device.xml))
        for key in set(before) - {'flutter.oc.activeProfile', *KEYS}:
            self.assertTrue(equal(before[key], after.get(key)), key)

    def test_original_absence_removes_only_the_selected_three_keys(self):
        self.pointer = None
        self.policy = dict.fromkeys(KEYS)
        self.call()
        after = values(ET.fromstring(self.device.xml))
        self.assertTrue({'flutter.oc.activeProfile', *KEYS}.isdisjoint(after))
        self.assertIn('flutter.oc.auth.owner', after)

    def test_force_stop_precedes_inventory_read_and_write(self):
        self.call()
        events = self.device.events
        self.assertEqual(events[0][0], ('shell', 'am', 'force-stop', PACKAGE))
        first_inventory = next(i for i, event in enumerate(events) if event[0] == ('mock-inventory',))
        first_cat = next(i for i, event in enumerate(events) if event[0] == 'cat')
        write = next(i for i, event in enumerate(events) if event[0] == 'write')
        self.assertLess(0, first_inventory)
        self.assertLess(first_inventory, first_cat)
        self.assertLess(first_cat, write)
        self.assertEqual(events[write - 1][0], ('mock-inventory',))
        self.assertEqual(events[write + 1][0], ('mock-inventory',))

    def test_delayed_whole_uid_death_is_confirmed_before_write(self):
        self.device.inventory = [{60: 1}, {80: 60}, {}]
        self.call()
        self.assertGreaterEqual(self.clock, .2)
        self.assertEqual(len(self.device.writes), 1)

    def test_permanently_live_uid_never_writes_and_times_out_within_ten_seconds(self):
        self.device.default_inventory = {60: 1}
        with self.assertRaisesRegex(Refused, '^final_metadata_uid_still_live$'):
            self.call()
        self.assertLessEqual(self.clock, 10)
        self.assertEqual(self.device.writes, [])
        self.assertFalse(any(event[0] == 'cat' for event in self.device.events))
        self.assertEqual(self.lines, [])
        self.assertTrue(all(0 < event[1] <= 3 for event in self.device.events
                            if event[0] == ('mock-inventory',)))

    def test_uid_returning_before_write_is_refused_without_writing(self):
        self.device.inventory = [{}, {60: 1}]
        with self.assertRaisesRegex(Refused, '^final_metadata_uid_still_live$'):
            self.call()
        self.assertEqual(self.device.writes, [])
        self.assertEqual(self.lines, [])

    def test_uid_returning_after_write_prevents_success_claim(self):
        self.device.inventory = [{}, {}, {60: 1}]
        with self.assertRaisesRegex(Refused, '^final_metadata_uid_still_live$'):
            self.call()
        self.assertEqual(len(self.device.writes), 1)
        self.assertEqual(self.lines, [])

    def test_failed_force_stop_does_not_read_or_write_preferences(self):
        self.device.force_stop_code = 1
        with self.assertRaisesRegex(Refused, '^final_metadata_force_stop_failed$'):
            self.call()
        self.assertEqual(len(self.device.events), 1)
        self.assertEqual(self.device.writes, [])

    def test_missing_malformed_duplicate_or_unavailable_uid_refuses_without_write(self):
        for raw in ['', 'package:other uid:10234\n', 'package:' + PACKAGE + ' uid:1\n',
                    self.device.package_stdout * 2, 'x' * 4097]:
            self.device = FakeDevice()
            self.device.package_stdout = raw
            with self.subTest(raw_length=len(raw)), self.assertRaisesRegex(Refused, '^final_metadata_uid_unavailable$'):
                self.call()
            self.assertEqual(self.device.writes, [])
        self.device = FakeDevice()
        self.device.package_code = 1
        with self.assertRaisesRegex(Refused, '^final_metadata_uid_unavailable$'):
            self.call()
        self.assertEqual(self.device.writes, [])

    def test_duplicate_selected_key_refuses_and_never_writes(self):
        self.device.xml = self.device.xml.replace('</map>', '<string name="' + KEYS[0] + '">duplicate</string></map>')
        with self.assertRaisesRegex(Refused, '^final_metadata_duplicate_key$'):
            self.call()
        self.assertEqual(self.device.writes, [])

    def test_silent_write_failure_cannot_claim_typed_restoration(self):
        self.device.ignore_write = True
        with self.assertRaisesRegex(Refused, '^final_metadata_typed_restoration_failed$'):
            self.call()
        self.assertEqual(self.lines, [])

    def test_active_pointer_type_is_verified_as_well_as_text(self):
        self.pointer = ET.fromstring('<long name="flutter.oc.activeProfile" value="3" />')
        self.call()
        self.assertTrue(equal(self.pointer, values(ET.fromstring(self.device.xml))['flutter.oc.activeProfile']))

    def test_bound_device_capability_and_outer_finally_share_the_same_helper(self):
        tree = ast.parse(self.source)
        binding = next(node for node in tree.body if isinstance(node, ast.Assign) and
                       ast.unparse(node.targets[0]) == 'H.Device.restore_original_metadata_before_comparison')
        self.assertEqual(ast.unparse(binding.value), 'restore_original_metadata')
        final = next(node for node in tree.body if isinstance(node, ast.Try))
        calls = [node for node in ast.walk(ast.Module(body=final.finalbody, type_ignores=[]))
                 if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and
                 node.func.id == 'restore_original_metadata']
        self.assertEqual(len(calls), 1)
        self.assertEqual(ast.unparse(calls[0].args[0]), 'd')
        helper = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == 'restore_original_metadata')
        self.assertNotIn("'install'", ast.unparse(helper))
        self.assertNotIn("'clear'", ast.unparse(helper))


def red_proofs():
    original = MetadataTests.source
    mutants = [
        ('selective_write', " device.write_dead(H.FLUTTER,ET.tostring(current_tree,encoding='unicode'))\n",
         '', 'test_exact_typed_restoration_and_pointer_still_get_compared'),
        ('force_stop', " H.require(device.adb('shell','am','force-stop',H.PACKAGE,timeout=5).returncode==0,\n           'final_metadata_force_stop_failed')\n",
         '', 'test_force_stop_precedes_inventory_read_and_write'),
        ('whole_uid_prewrite', " H.require(not H.uid_inventory(device,uid),'final_metadata_uid_still_live')\n device.write_dead",
         ' device.write_dead', 'test_uid_returning_before_write_is_refused_without_writing'),
    ]
    try:
        for label, before, after, test_name in mutants:
            assert original.count(before) == 1, label
            MetadataTests.source = original.replace(before, after)
            result = unittest.TestResult()
            MetadataTests(test_name).run(result)
            assert not result.wasSuccessful(), 'Removed restoration fix unexpectedly passed: ' + label
            print(f'PASS removed_fix_failed {label} tests={result.testsRun} failures={len(result.failures)} errors={len(result.errors)}')
    finally:
        MetadataTests.source = original
    print('PASS source_restored_without_filesystem_mutation')


if __name__ == '__main__':
    if sys.argv[1:] == ['--red']:
        red_proofs()
    else:
        unittest.main()
