"""Privacy and bounds for the pure device log projection; no ADB or processes."""

import json
import unittest

from .diagnostic_logs import MAX_BYTES, MAX_EVENTS, project_log


START = 1791490740000  # 2026-10-08T20:19:00Z
END = START + 120000


class DiagnosticLogTests(unittest.TestCase):
    def app(self, raw, **kwargs):
        return project_log(
            raw,
            source="app",
            window_start_ms=START,
            window_end_ms=END,
            app_pids={2121, 2122},
            **kwargs,
        )

    def server(self, raw):
        return project_log(
            raw, source="server", window_start_ms=START, window_end_ms=END
        )

    def test_actual_native_style_keeps_only_verified_pids_and_window(self):
        result = self.app(
            b"1791490740.001  2121  2121 W OcLinux: notification not updated\n"
            b"1791490800.123  2122  2140 W OcLinux: running services not recorded\n"
            b"1791490800.123  9999  9999 E OcLinux: install failed\n"
            b"1791490739.999  2121  2121 E OcLinux: install failed\n"
            b"1791490860.001  2121  2121 E OcLinux: install failed\n"
        )
        self.assertEqual(
            result["events"],
            [
                {
                    "timestampMs": START + 1,
                    "level": "warning",
                    "category": "notification_update_failed",
                },
                {
                    "timestampMs": START + 60123,
                    "level": "warning",
                    "category": "service_state_record_failed",
                },
            ],
        )
        self.assertTrue(result["captured"])
        self.assertEqual(result["omittedLines"], 3)

    def test_no_pid_authority_does_not_emit_app_events(self):
        result = project_log(
            b"1791490740.001 2121 2121 E OcLinux: install failed\n",
            source="app",
            window_start_ms=START,
            window_end_ms=END,
        )
        self.assertEqual(result["events"], [])

    def test_private_lines_and_arbitrary_suffixes_cannot_mimic_native_events(self):
        raw = (
            b"1791490740.001 2121 2121 I flutter: message=notification not updated\n"
            b"1791490740.001 2121 2121 I OcLinux: prompt: install failed\n"
            b"1791490740.001 2121 2121 E OcLinux: install failed for account alice@example.com\n"
            b"1791490740.001 2121 2121 I OcLinux: Basic c2VjcmV0 sk-private\n"
            b"1791490740.001 2121 2121 I OcLinux: INFO 2026-10-08T20:19:00Z service=session.prompt error\n"
        )
        result = self.app(raw)
        self.assertEqual(result["events"], [])
        self.assertNotIn("alice", json.dumps(result))
        self.assertNotIn("private", json.dumps(result))

    def test_fixed_lifecycle_reason_drops_native_numeric_details(self):
        result = self.app(
            b"1791490740.001 2121 2121 I OcLifecycle: previous process ended: reason=4 status=0\n"
        )
        self.assertEqual(result["events"][0]["category"], "previous_process_exit")
        self.assertEqual(set(result["events"][0]), {"timestampMs", "level", "category"})

    def test_verified_native_diagnostic_failure_literals(self):
        result = self.app(
            b"1791490740.001 2121 2121 W OcLinux: Service diagnostics could not be saved\n"
            b"1791490740.002 2121 2121 W OcLifecycle: exit reasons unavailable\n"
        )
        self.assertEqual(
            [e["category"] for e in result["events"]],
            ["service_diagnostics_write_failed", "exit_history_unavailable"],
        )

    def test_actual_server_style_parses_structured_metadata_not_private_values(self):
        result = self.server(
            b"INFO  2026-10-08T20:19:00.123Z +19ms service=session.prompt sessionID=ses_private step=10 loop\n"
            b'ERROR 2026-10-08T20:20:00Z +0ms service=session.prompt error="private transcript sk-private" error\n'
            b"INFO  2026-10-08T20:20:30+00:00 +1ms service=bus type=session.error publishing\n"
        )
        self.assertEqual(
            [e["category"] for e in result["events"]],
            ["session_prompt_loop", "session_prompt_error", "session_error_published"],
        )
        self.assertEqual(result["events"][0]["timestampMs"], START + 123)
        self.assertNotIn("ses_private", json.dumps(result))
        self.assertNotIn("transcript", json.dumps(result))

    def test_frozen_effect_logger_projects_real_session_log_call_shapes(self):
        result = self.server(
            b"timestamp=2026-10-08T20:19:00.123Z level=INFO run=private-run message=loop session.id=ses_private step=10\n"
            b"timestamp=2026-10-08T20:19:01.000Z level=Info run=private-run message=process session.id=ses_private messageID=msg_private\n"
            b'timestamp=2026-10-08T20:20:00Z level=Error run=private-run message=process session.id=ses_private messageID=msg_private error="private conversation Basic SECRET" stack="private stack"\n'
            b'timestamp=2026-10-08T20:20:01Z level=INFO run=private-run message="exiting loop" session.id=ses_private\n'
            b"timestamp=2026-10-08T20:20:02Z level=INFO run=private-run message=cancel session.id=ses_private\n"
        )
        self.assertEqual(
            [e["category"] for e in result["events"]],
            [
                "session_prompt_loop",
                "session_processor_start",
                "session_processor_error",
                "session_prompt_loop_exit",
                "session_prompt_cancel",
            ],
        )
        self.assertEqual(result["events"][0]["timestampMs"], START + 123)
        encoded = json.dumps(result)
        for private in (
            "private-run",
            "ses_private",
            "msg_private",
            "conversation",
            "SECRET",
            "stack",
        ):
            self.assertNotIn(private, encoded)

    def test_frozen_execution_error_allows_actual_repeated_message_not_fake_headers(
        self,
    ):
        result = self.server(
            b'timestamp=2026-10-08T20:19:00Z level=ERROR run=private-run message="Failed to drain Session" message="private Cause pretty output" sessionID=ses_private cause="private provider token"\n'
            b"timestamp=2026-10-08T20:19:00Z level=INFO run=private-run message=loop session.id=ses_private step=1 level=ERROR\n"
            b"timestamp=2026-10-08T20:19:00Z level=INFO run=private-run message=loop session.id=ses_private step=1 timestamp=2026-10-08T20:20:00Z\n"
        )
        self.assertEqual(
            [e["category"] for e in result["events"]], ["session_prompt_error"]
        )

    def test_frozen_private_fields_cannot_replace_message_or_supply_lifecycle_signature(
        self,
    ):
        result = self.server(
            b'timestamp=2026-10-08T20:19:00Z level=ERROR run=private-run message="private conversation" cause="message=loop session.id=ses_private step=1"\n'
            b"timestamp=2026-10-08T20:19:00Z level=ERROR run=private-run error.message=loop session.id=ses_private step=1\n"
            b"timestamp=2026-10-08T20:19:00Z level=ERROR run=private-run message=loop\n"
            b"timestamp=2026-10-08T20:19:00Z level=INFO run=private-run message=publishing type=session.error\n"
            b"timestamp=2026-10-08T20:19:00Z level=INFO run=private-run message=loop session.id=ses_private step=1 trailing-private-text\n"
            b"timestamp=2026-10-08T20:19:00Z level=UNKNOWN run=private-run message=loop session.id=ses_private step=1\n"
        )
        self.assertEqual(result["events"], [])
        self.assertEqual(result["omittedLines"], 6)

    def test_frozen_levels_timestamps_causes_and_ansi_are_bounded_safe(self):
        result = self.server(
            b'\x1b[31mtimestamp="2026-10-08T20:19:00+00:00" level=INFO run=private-run message=loop session.id=ses_private step=1 cause="{ \\"message\\": \\"SECRET\\" }"\x1b[0m\n'
            b"timestamp=2026-10-08T20:18:59Z level=INFO run=private-run message=loop session.id=ses_private step=1\n"
            b"timestamp=not-a-time level=INFO run=private-run message=loop session.id=ses_private step=1\n"
        )
        self.assertEqual(len(result["events"]), 1)
        self.assertEqual(result["timeUnavailableLines"], 1)
        self.assertNotIn("SECRET", json.dumps(result))

    def test_private_structured_values_do_not_become_log_signatures(self):
        result = self.server(
            b'INFO 2026-10-08T20:19:00Z +0ms service=other text="service=session.prompt error" message\n'
            b'ERROR 2026-10-08T20:19:00Z service=session.prompt error="x service=bus type=session.error publishing" arbitrary\n'
            b'INFO 2026-10-08T20:19:00Z service=session.prompt error="unterminated loop\n'
            b"INFO 2026-10-08T20:19:00Z service=session.prompt service=bus type=session.error publishing\n"
            b'INFO 2026-10-08T20:19:00Z service=session.prompt {"conversation":"loop"}\n'
            b'ERROR 2026-10-08T20:19:00Z service=session.prompt error={"message":"Basic SECRET","nested":{"a":"loop"}} error\n'
        )
        self.assertEqual(
            [e["category"] for e in result["events"]], ["session_prompt_error"]
        )
        self.assertNotIn("SECRET", json.dumps(result))

    def test_iso_offset_and_utc_timestamp_have_equal_milliseconds(self):
        result = self.server(
            b"INFO 2026-10-08T21:19:00+01:00 service=session.processor start\n"
            b"INFO 2026-10-08T20:19:00 service=session.processor start\n"
        )
        self.assertEqual([e["timestampMs"] for e in result["events"]], [START, START])

    def test_threadtime_is_not_falsely_attributed_to_the_epoch_window(self):
        result = self.app(b"10-08 20:19:00.123 2121 2121 E OcLinux: install failed\n")
        self.assertEqual(result["events"], [])
        self.assertEqual(result["timeUnavailableLines"], 1)

    def test_untimestamped_and_invalid_timestamp_lines_are_counted_not_invented(self):
        result = self.server(
            b"ERROR service=session.prompt error\n"
            b"ERROR 2026-99-08T20:19:00Z service=session.prompt error\n"
            b"ERROR 2026-10-08T20:19:00Z service=session.prompt error\n"
        )
        self.assertEqual(len(result["events"]), 1)
        self.assertEqual(result["omittedLines"], 2)

    def test_byte_bound_keeps_tail_and_drops_incomplete_first_line(self):
        raw = (
            b"x" * MAX_BYTES
            + b"\nINFO 2026-10-08T20:19:00Z service=session.processor start\n"
        )
        result = self.server(raw)
        self.assertTrue(result["truncated"])
        self.assertEqual(result["inputBytes"], len(raw))
        self.assertLessEqual(result["processedBytes"], MAX_BYTES)
        self.assertEqual(len(result["events"]), 1)

    def test_event_bound_reports_dropped_categories(self):
        raw = b"INFO 2026-10-08T20:19:00Z service=session.processor start\n" * (
            MAX_EVENTS + 30
        )
        result = self.server(raw)
        self.assertEqual(len(result["events"]), MAX_EVENTS)
        self.assertTrue(result["eventsTruncated"])
        self.assertEqual(result["matchedLines"], MAX_EVENTS + 30)

    def test_binary_input_is_safe_and_absent_capture_remains_unavailable(self):
        self.assertEqual(self.server(b"\xff\xfe private")["events"], [])
        result = self.server(None)
        self.assertFalse(result["captured"])
        self.assertEqual(result["inputBytes"], 0)

    def test_contract_arguments_fail_with_fixed_error_without_echo(self):
        for values in [
            {"source": "private-source"},
            {"window_start_ms": True},
            {"window_end_ms": START - 1},
            {"app_pids": {"private-pid"}},
        ]:
            args = {
                "source": "app",
                "window_start_ms": START,
                "window_end_ms": END,
                "app_pids": {2121},
            }
            args.update(values)
            with self.assertRaisesRegex(
                ValueError, "^invalid_log_projection_contract$"
            ):
                project_log(b"private password", **args)
