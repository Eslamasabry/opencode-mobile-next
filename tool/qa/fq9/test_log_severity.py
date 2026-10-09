import json, unittest
from .log_severity import project_severity


class SeverityTests(unittest.TestCase):
    def test_unknown_error_messages_are_counted_without_exporting_private_text(self):
        raw = b"timestamp=2026-10-08T23:35:00.000Z level=ERROR run=PRIVATE message=PRIVATE_PASSWORD\n"
        r = project_severity(
            raw, source="server", start_ms=1791502400000, end_ms=1791502600000
        )
        self.assertEqual(r["levelCounts"], {"error": 1})
        self.assertNotIn("PRIVATE", json.dumps(r))

    def test_foreign_times_and_embedded_fake_headers_do_not_count(self):
        raw = b'1791502500.001 123 124 W flutter: PRIVATE_ACCOUNT\nmessage="timestamp=2026-10-08T23:35:00Z level=ERROR"\n1791502700.000 123 124 E flutter: PRIVATE\n'
        r = project_severity(
            raw, source="app", start_ms=1791502400000, end_ms=1791502600000
        )
        self.assertEqual(r["levelCounts"], {"warning": 1})
        self.assertNotIn("PRIVATE", json.dumps(r))
        self.assertEqual(r["omittedLines"], 2)
