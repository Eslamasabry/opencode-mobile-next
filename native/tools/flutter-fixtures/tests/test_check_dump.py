"""B-MIGR-004 fixture dump checks (E00-S02). Run: python3 -I -m unittest discover -s native/tools/flutter-fixtures/tests"""
import io
import json
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import check_dump  # noqa: E402
import xml_to_prefs_json  # noqa: E402

REPO = HERE.parents[3]
LEDGER = REPO / 'docs/native/migration/store-ledger.json'
TEMPLATE = REPO / 'native/e2e/fixtures/flutter-2.1.4/seed-manifest.json'

PREFS_XML = b"""<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="flutter.oc.profiles">[{&quot;id&quot;:&quot;p1&quot;}]</string>
    <string name="flutter.oc.activeProfile">p1</string>
    <boolean name="flutter.oc.cleartextOk.p1" value="true" />
    <long name="flutter.oc.notify.quietStart" value="1320" />
    <set name="flutter.oc.agentTurnFailures.p1"><string>a</string></set>
</map>
"""


def make_dump(root: Path, prefs_xml: bytes = PREFS_XML, fss: bytes = b'<map><string name="X_pw.p1">ct</string></map>') -> Path:
    def add(t, name, data):
        info = tarfile.TarInfo(name)
        info.size = len(data)
        t.addfile(info, io.BytesIO(data))
    with tarfile.open(root / 'shared_prefs.tar', 'w') as t:
        add(t, 'shared_prefs/FlutterSharedPreferences.xml', prefs_xml)
        add(t, 'shared_prefs/FlutterSecureStorage.xml', fss)
        add(t, 'shared_prefs/builtin_server_recovery.xml', b'<map />')
    (root / 'tree.txt').write_text('directory|4096|files/linux\nregular file|10|files/draft-attachments-v1/a/b.blob\n')
    for f in check_dump.OS_FILES:
        (root / f).write_text(f'Package: io.github.eslamasabry.opencode_mobile .SessionsWidgetProvider oc.session:p1:s1\n')
    m = json.loads(TEMPLATE.read_text())
    m['build'].update(commit='a' * 40, versionCode=4612, signerSha256=check_dump.LOCAL_RELEASE_KEY)
    m['avd']['name'] = 'OC_E2E_API37'
    m['reviewed'].update(by='coordinator', at='2026-11-06')
    m['seeds'] = [
        {'id': 'oc2-password', 'expect': ['oc.profiles', 'oc.activeProfile', 'pw.']},
        {'id': 'lan-cleartext', 'expect': ['oc.cleartextOk.']},
        {'id': 'draft-photo', 'expect': ['draft-attachments-v1']},
        {'id': 'linux', 'expect': ['linux', 'builtin_server_recovery']},
        {'id': 'surfaces', 'expect': ['.SessionsWidgetProvider', 'oc.session:']},
    ]
    (root / 'seed-manifest.json').write_text(json.dumps(m))
    xml_to_prefs_json.main(['x', str(root)])
    return root


class CheckDumpTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_B_MIGR_004_a_complete_reviewed_dump_is_clean(self):
        self.assertEqual([], check_dump.check(make_dump(self.dir), LEDGER))

    def test_B_MIGR_004_the_committed_template_is_not_yet_a_dump(self):
        problems = check_dump.check(TEMPLATE.parent, LEDGER)
        self.assertIn('missing file shared_prefs.tar', problems)
        self.assertIn('build.commit is not a full commit id', problems)
        self.assertTrue(any('versionCode' in p for p in problems))

    def test_B_MIGR_004_every_template_seed_names_ledger_rows(self):
        ledger = check_dump.load_ledger(LEDGER)
        seeds = json.loads(TEMPLATE.read_text())['seeds']
        self.assertEqual([], [f'{s["id"]}:{n}' for s in seeds for n in s['expect'] if n not in ledger])

    def test_B_MIGR_004_low_version_code_wrong_signer_and_walk_avd_fail(self):
        d = make_dump(self.dir)
        m = json.loads((d / 'seed-manifest.json').read_text())
        m['build'].update(versionCode=62, signerSha256='842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C')
        m['avd']['name'] = 'OC_WALK'
        (d / 'seed-manifest.json').write_text(json.dumps(m))
        problems = check_dump.check(d, LEDGER)
        self.assertTrue(any('versionCode' in p for p in problems))
        self.assertIn('build.signerSha256 is not the local release key', problems)
        self.assertTrue(any('walk AVD' in p for p in problems))

    def test_B_MIGR_004_a_seeded_key_missing_from_the_dump_is_named(self):
        d = make_dump(self.dir, prefs_xml=PREFS_XML.replace(b'flutter.oc.cleartextOk.p1', b'flutter.oc.other.p1'))
        self.assertEqual(['seed lan-cleartext: oc.cleartextOk. is not in the dump'], check_dump.check(d, LEDGER))

    def test_B_MIGR_004_a_real_looking_token_fails_without_echoing_it(self):
        token = 'ghp_' + 'Q' * 36
        d = make_dump(self.dir, prefs_xml=PREFS_XML.replace(b'>p1</string>', f'>{token}</string>'.encode()))
        problems = check_dump.check(d, LEDGER)
        self.assertEqual(['prefs.json holds something shaped like a real credential; remove it and reseed'], problems)
        self.assertFalse(any(token in p for p in problems))

    def test_B_MIGR_004_prefs_json_keeps_types_and_order(self):
        prefs = xml_to_prefs_json.convert(PREFS_XML)
        self.assertEqual(['flutter.oc.profiles', 'flutter.oc.activeProfile', 'flutter.oc.cleartextOk.p1',
                          'flutter.oc.notify.quietStart', 'flutter.oc.agentTurnFailures.p1'], list(prefs))
        self.assertEqual({'type': 'long', 'value': 1320}, prefs['flutter.oc.notify.quietStart'])
        self.assertEqual({'type': 'set', 'value': ['a']}, prefs['flutter.oc.agentTurnFailures.p1'])


if __name__ == '__main__':
    unittest.main()
