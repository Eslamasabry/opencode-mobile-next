"""Failing-first terminal evidence projection, without any device access."""

import copy
import json
import unittest

from .common import DriverFailure
from .terminal import project_terminal


RECEIPT = {
    "engine": "opencode",
    "directory": "/root/projects/fq9-owned",
    "sessions": [
        {"id": "ses_owned", "title": "fq9-owned-background", "promptID": "msg_user"}
    ],
}


def history(*, finish=None, completed=None, error=None, tool_status="running"):
    info = {
        "id": "msg_assistant",
        "role": "assistant",
        "sessionID": "ses_owned",
        "parentID": "msg_user",
        "time": {"created": 1001},
    }
    if finish is not None:
        info["finish"] = finish
    if completed is not None:
        info["time"]["completed"] = completed
    if error is not None:
        info["error"] = error
    return [
        {
            "info": {
                "id": "msg_user",
                "role": "user",
                "sessionID": "ses_owned",
                "time": {"created": 1000},
            },
            "parts": [{"type": "text", "text": "PRIVATE_PROMPT"}],
        },
        {
            "info": info,
            "parts": [
                {
                    "type": "tool",
                    "sessionID": "ses_owned",
                    "messageID": "msg_assistant",
                    "tool": "bash",
                    "callID": "call_owned",
                    "state": {
                        "status": tool_status,
                        "time": {"start": 1100, "end": 1200},
                        "input": {"command": "PRIVATE_COMMAND", "timeout": 2400000},
                        "output": "PRIVATE_OUTPUT",
                        "error": "PRIVATE_ERROR",
                        "metadata": {"secret": "PRIVATE_METADATA"},
                    },
                }
            ],
        },
    ]


class TerminalProjectionTest(unittest.TestCase):
    def project(self, messages=None, status=None):
        return project_terminal(
            history() if messages is None else messages,
            {"ses_owned": {"type": "busy"}} if status is None else status,
            copy.deepcopy(RECEIPT),
        )

    def test_busy_tool_is_running_and_exports_only_safe_fields(self):
        result = self.project()
        self.assertEqual(result["outcome"], "running")
        self.assertEqual(result["sessionState"], "busy")
        self.assertEqual(result["latestUser"], {"created": 1000, "completed": None})
        self.assertEqual(result["tools"]["counts"]["running"], 1)
        self.assertEqual(
            result["tools"]["timings"],
            [{"state": "running", "start": 1100, "end": 1200, "timeoutMs": 2400000}],
        )
        serialized = json.dumps(result)
        self.assertNotIn("PRIVATE", serialized)
        self.assertNotIn("ses_owned", serialized)
        self.assertNotIn("call_owned", serialized)

    def test_completed_stop_differs_from_idle_unfinished(self):
        done = self.project(history(finish="stop", completed=3000), {})
        self.assertEqual(done["outcome"], "completed")
        self.assertEqual(done["latestAssistant"]["finishReason"], "stop")
        self.assertEqual(done["latestAssistant"]["completed"], 3000)
        self.assertEqual(self.project(history(), {})["outcome"], "inactive")

    def test_tool_calls_is_not_a_completed_turn(self):
        result = self.project(history(finish="tool-calls", completed=3000), {})
        self.assertEqual(result["outcome"], "inactive")

    def test_terminal_length_and_content_filter_preserve_reason(self):
        for finish in ("length", "content-filter"):
            with self.subTest(finish=finish):
                result = self.project(history(finish=finish, completed=3000), {})
                self.assertEqual(result["outcome"], "completed")
                self.assertEqual(result["latestAssistant"]["finishReason"], finish)

    def test_idle_failed_tool_is_error_without_exporting_tool_error_text(self):
        result = self.project(history(tool_status="error"), {})
        self.assertEqual(result["outcome"], "errored")
        self.assertNotIn("PRIVATE", json.dumps(result))

    def test_aborted_is_not_generic_error_even_if_status_busy(self):
        result = self.project(
            history(
                error={"name": "MessageAbortedError", "data": {"message": "PRIVATE"}}
            )
        )
        self.assertEqual(result["outcome"], "aborted")
        self.assertEqual(
            result["latestAssistant"]["errorCategory"], "MessageAbortedError"
        )

    def test_known_error_and_hostile_error_name(self):
        for name, category in [
            ("APIError", "APIError"),
            ("PRIVATE_SECRET", "unknown"),
            ({"private": "secret"}, "unknown"),
        ]:
            with self.subTest(name=name):
                result = self.project(history(error={"name": name}))
                self.assertEqual(result["outcome"], "errored")
                self.assertEqual(result["latestAssistant"]["errorCategory"], category)
                self.assertNotIn("PRIVATE", json.dumps(result))

    def test_retry_omits_message_and_bounds_numeric_metadata(self):
        result = self.project(
            status={
                "ses_owned": {
                    "type": "retry",
                    "attempt": 2,
                    "next": 4000,
                    "message": "PRIVATE",
                }
            }
        )
        self.assertEqual(result["outcome"], "retrying")
        self.assertEqual(result["retry"], {"attempt": 2, "next": 4000})
        self.assertNotIn("PRIVATE", json.dumps(result))

    def test_unknown_enums_do_not_export_arbitrary_values(self):
        result = self.project(
            history(finish="PRIVATE_FINISH", tool_status="PRIVATE_STATE"),
            {"ses_owned": {"type": "PRIVATE_STATUS"}},
        )
        self.assertEqual(result["outcome"], "unknown")
        self.assertEqual(result["latestAssistant"]["finishReason"], "unknown")
        self.assertEqual(result["tools"]["counts"]["unknown"], 1)
        self.assertNotIn("PRIVATE", json.dumps(result))

    def test_latest_owned_assistant_replaces_previous_terminal_message(self):
        messages = history(finish="stop", completed=3000)
        newest = copy.deepcopy(messages[-1])
        newest["info"]["id"] = "msg_latest"
        newest["info"]["time"] = {"created": 3100}
        newest["info"].pop("finish")
        newest["parts"] = []
        messages.append(newest)
        result = self.project(messages, {})
        self.assertEqual(result["outcome"], "inactive")
        self.assertEqual(result["latestAssistant"]["created"], 3100)

    def test_foreign_session_message_and_tool_refuse(self):
        for location in ("message", "tool", "parent"):
            messages = history()
            if location == "message":
                messages[-1]["info"]["sessionID"] = "ses_foreign"
            elif location == "tool":
                messages[-1]["parts"][0]["sessionID"] = "ses_foreign"
            else:
                messages[-1]["parts"][0]["messageID"] = "msg_foreign"
            with (
                self.subTest(location=location),
                self.assertRaisesRegex(DriverFailure, "terminal_scope_mismatch"),
            ):
                self.project(messages)

    def test_new_user_prompt_refuses_even_in_owned_session(self):
        messages = history()
        newest = copy.deepcopy(messages[0])
        newest["info"]["id"] = "msg_foreign"
        messages.append(newest)
        with self.assertRaisesRegex(DriverFailure, "terminal_prompt_mismatch"):
            self.project(messages)

    def test_malformed_or_unbounded_inputs_refuse_fixed_errors(self):
        for bad in (None, {}, [], [None], history() * 100):
            with self.subTest(bad=type(bad)), self.assertRaises(DriverFailure):
                project_terminal(bad, {}, RECEIPT)
        for number in (True, float("inf"), -1, 2**53):
            messages = history()
            messages[1]["info"]["time"]["created"] = number
            with (
                self.subTest(number=number),
                self.assertRaisesRegex(DriverFailure, "terminal_history_invalid"),
            ):
                self.project(messages)

    def test_wrong_receipt_and_invalid_status_refuse(self):
        receipt = copy.deepcopy(RECEIPT)
        receipt["engine"] = "opencode2"
        with self.assertRaisesRegex(DriverFailure, "terminal_receipt_invalid"):
            project_terminal(history(), {}, receipt)
        for status in (
            [],
            {"ses_owned": []},
            {"ses_owned": {"type": "retry", "next": True}},
        ):
            with (
                self.subTest(status=status),
                self.assertRaisesRegex(DriverFailure, "terminal_status_invalid"),
            ):
                self.project(status=status)

    def test_no_assistant_is_inactive_not_completed(self):
        result = self.project(history()[:1], {})
        self.assertEqual(result["outcome"], "inactive")
        self.assertIsNone(result["latestAssistant"])
