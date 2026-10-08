"""Synthetic parser unit fixtures are never used as Android device evidence."""
import json
import unittest

from tool.qa.bd7_exit_proof import ProofFailure, parse_crash_ring, parse_exit_history

PACKAGE = 'io.github.eslamasabry.opencode_mobile'
PRIVATE = 'synthetic-private-value'


def exit_block(pid=1234, reason=4, process=PACKAGE, status=0, index=0):
    # AOSP ApplicationExitInfo.dump: metadata occupies the first two lines.
    return (
        f'      ApplicationExitInfo #{index}:\n'
        f'        timestamp=2026-10-08 12:34:56.789 pid={pid} realUid=10123 '
        'packageUid=10123 definingUid=10123 user=0\n'
        f'        process={process} reason={reason} (APP CRASH(EXCEPTION)) '
        f'subreason=0 (UNKNOWN) status={status}\n'
        f'        importance=100 pss=1.0MB rss=2.0MB description={PRIVATE} '
        f'state=empty trace=/private/{PRIVATE}\n'
    )


def history(*blocks, package=PACKAGE):
    return ('ACTIVITY MANAGER PROCESS EXIT INFO (dumpsys activity exit-info)\n'
            f'  package: {package}\n'
            '    Historical Process Exit for uid=10123\n' + ''.join(blocks)).encode()


def ring(records):
    return json.dumps(records, separators=(',', ':')).encode()


class ExitProofTest(unittest.TestCase):
    def parse(self, data, reason=4):
        return parse_exit_history(data, PACKAGE, 1234, reason)

    def rejected(self, data):
        with self.assertRaises(ProofFailure) as raised:
            self.parse(data)
        self.assertEqual(str(raised.exception), 'exit_proof_invalid')
        self.assertNotIn(PRIVATE, str(raised.exception))

    def test_real_format_numeric_projection(self):
        self.assertEqual(self.parse(history(exit_block())),
                         {'pid': 1234, 'reason': 4, 'status': 0, 'exact_main': True})

    def test_anr_reason_six_is_not_a_force_stop(self):
        self.assertEqual(self.parse(history(exit_block(reason=6)), reason=6)['reason'], 6)
        self.rejected(history(exit_block(reason=10)))

    def test_requested_pid_can_follow_other_historical_records(self):
        self.assertEqual(self.parse(history(exit_block(pid=4567), exit_block(index=1)))['pid'], 1234)

    def test_wrong_package_is_rejected(self):
        self.rejected(history(exit_block(), package=PACKAGE + '.preview'))

    def test_secondary_process_is_rejected(self):
        self.rejected(history(exit_block(process=PACKAGE + ':worker')))

    def test_unscoped_record_is_rejected(self):
        self.rejected(exit_block().encode())

    def test_wrong_or_missing_pid_is_rejected(self):
        self.rejected(history(exit_block(pid=5678)))
        self.rejected(history(exit_block()).replace(b'pid=1234 ', b''))

    def test_missing_or_malformed_reason_status_are_rejected(self):
        for before, after in [(b'reason=4 ', b''), (b'reason=4 ', b'reason=secret '),
                              (b'status=0', b'status=bad'), (b'status=0', b'')]:
            with self.subTest(before=before):
                self.rejected(history(exit_block()).replace(before, after))

    def test_duplicate_pid_record_is_rejected(self):
        self.rejected(history(exit_block(), exit_block(index=1)))

    def test_duplicate_contradictory_pid_record_is_rejected(self):
        self.rejected(history(exit_block(), exit_block(reason=6, index=1)))

    def test_duplicate_metadata_field_is_rejected(self):
        self.rejected(history(exit_block()).replace(b'pid=1234 ', b'pid=1234 pid=5678 '))

    def test_descriptions_cannot_supply_missing_metadata(self):
        data = history(exit_block()).replace(b'process=' + PACKAGE.encode(), b'process=other')
        data += f'        description=process={PACKAGE} reason=4 pid=1234 status=0\n'.encode()
        self.rejected(data)

    def test_input_cap_and_invalid_utf8_fail_fixed(self):
        self.rejected(history(exit_block()) + b' ' * (2 * 1024 * 1024))
        self.rejected(history(exit_block()) + b'\xff')


class CrashRingTest(unittest.TestCase):
    def parse(self, raw):
        return parse_crash_ring(raw, 'native', 1000)

    def rejected(self, raw):
        with self.assertRaises(ProofFailure) as raised:
            self.parse(raw)
        self.assertEqual(str(raised.exception), 'crash_ring_invalid')
        self.assertNotIn(PRIVATE, str(raised.exception))

    def test_native_projection_selects_newest_real_record(self):
        records = [{'source': 'native', 'category': 'Native application error', 'time': 1001},
                   {'source': 'anr', 'category': 'Android reported that the app stopped responding', 'time': 1005},
                   {'source': 'native', 'category': 'Native application error', 'time': 1003}]
        self.assertEqual(self.parse(ring(records)), records[2])

    def test_anr_record_has_fixed_source_category_time(self):
        record = {'source': 'anr', 'category': 'Android reported that the app stopped responding', 'time': 1001}
        self.assertEqual(parse_crash_ring(ring([record]), 'anr', 1000), record)

    def test_before_or_equal_epoch_is_not_new_evidence(self):
        for value in [999, 1000]:
            self.rejected(ring([{'source': 'native', 'category': 'Native application error', 'time': value}]))

    def test_unknown_fields_never_escape(self):
        self.rejected(ring([{'source': 'native', 'category': 'Native application error',
                             'time': 1001, 'message': PRIVATE}]))

    def test_every_record_is_validated_including_other_sources(self):
        self.rejected(ring([{'source': 'native', 'category': 'Native application error', 'time': 1001},
                            {'source': 'other', 'category': PRIVATE, 'time': 1002}]))

    def test_unknown_or_missing_category_is_rejected(self):
        self.rejected(ring([{'source': 'native', 'category': PRIVATE, 'time': 1001}]))
        self.rejected(ring([{'source': 'native', 'time': 1001}]))

    def test_timestamp_must_be_positive_integer_not_bool_float_or_string(self):
        for value in [True, 1001.0, '1001', -1, 0]:
            self.rejected(ring([{'source': 'native', 'category': 'Native application error', 'time': value}]))

    def test_duplicate_json_key_is_rejected(self):
        self.rejected(b'[{"source":"native","category":"Native application error","time":1001,"time":1002}]')

    def test_entry_and_byte_caps_are_enforced(self):
        record = {'source': 'native', 'category': 'Native application error', 'time': 1001}
        self.rejected(ring([record] * 21))
        self.rejected(ring([record]) + b' ' * 8192)

    def test_malformed_nonlist_and_invalid_utf8_fail_fixed(self):
        for raw in [b'{', b'{}', b'null', b'[]', b'\xff', b'[NaN]']:
            self.rejected(raw)


if __name__ == '__main__':
    unittest.main()
