"""Self-tests for servers/stub_model.py (the FX-OC2 deterministic model).

    python3 -I -m unittest discover -s native/e2e/fixtures/tests -t native/e2e/fixtures -p 'test_stub_model.py' -v
"""

from __future__ import annotations

import http.client
import json
import os
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path
from typing import Any

HERE = Path(__file__).resolve().parent
SERVERS = HERE.parent / "servers"
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(SERVERS))

import stub_model  # noqa: E402
from fxlib.core import free_port, wait_for_port  # noqa: E402

PROJECT = "/fx/projects/app"
SECRET_WORDS = "zebra-quartz-marker"


def tool(name: str, required: list[str], props: list[str] | None = None) -> dict[str, Any]:
    keys = props if props is not None else required
    return {"type": "function", "function": {"name": name, "parameters": {
        "type": "object", "properties": {k: {"type": "string"} for k in keys}, "required": required}}}


# What OC2 2.0.29 offers (verified names/args) plus a v1-style edit with filePath.
OC2_TOOLS = [tool("shell", ["command"]), tool("write", ["path", "content"]), tool("read", ["path"]),
             tool("edit", ["filePath", "oldString", "newString"]),
             tool("question", ["questions"]), tool("todowrite", ["todos"])]


def chat(prompt: str, tools: list[dict[str, Any]] | None = None, after: list[dict[str, Any]] | None = None,
         stream: bool = True, system: str = "You are a coding agent.") -> dict[str, Any]:
    body: dict[str, Any] = {"model": "stub", "stream": stream, "messages": [
        {"role": "system", "content": system}, {"role": "user", "content": prompt}, *(after or [])]}
    if tools is not None:
        body["tools"] = tools
    return body


def tool_round(name: str, n: int = 1) -> list[dict[str, Any]]:
    """The messages OpenCode sends back after one tool-call step."""
    calls = [{"id": f"c{i}", "type": "function", "function": {"name": name, "arguments": "{}"}} for i in range(n)]
    return [{"role": "assistant", "content": "", "tool_calls": calls},
            *[{"role": "tool", "tool_call_id": f"c{i}", "content": "ok"} for i in range(n)]]


class StubModelHttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.tmp = Path(tempfile.mkdtemp(prefix="fx-stub-"))
        cls.log = cls.tmp / "model.jsonl"
        cls.port = free_port()
        cls.srv = stub_model.start_in_thread(cls.port, log_path=cls.log, default_dir=PROJECT, speed=400)

    @classmethod
    def tearDownClass(cls) -> None:
        cls.srv.shutdown()
        cls.srv.server_close()
        shutil.rmtree(cls.tmp, ignore_errors=True)

    def post(self, body: dict[str, Any], timeout: float = 30) -> dict[str, Any]:
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=timeout)
        conn.request("POST", "/v1/chat/completions", json.dumps(body), {"Content-Type": "application/json"})
        resp = conn.getresponse()
        raw = resp.read()
        conn.close()
        self.assertEqual(resp.status, 200)
        if not body.get("stream"):
            data = json.loads(raw)
            choice = data["choices"][0]
            calls = [{"name": c["function"]["name"], "arguments": json.loads(c["function"]["arguments"])}
                     for c in choice["message"].get("tool_calls") or []]
            return {"text": choice["message"]["content"], "finish": choice["finish_reason"], "calls": calls,
                    "chunks": 1}
        events = [line[6:] for line in raw.decode().split("\n") if line.startswith("data: ")]
        self.assertEqual(events[-1], "[DONE]")
        text, finish, calls, chunks = "", None, [], 0
        for event in events[:-1]:
            choice = json.loads(event)["choices"][0]
            delta = choice.get("delta") or {}
            if delta.get("content"):
                text += delta["content"]
                chunks += 1
            for c in delta.get("tool_calls") or []:
                calls.append({"name": c["function"]["name"], "arguments": json.loads(c["function"]["arguments"])})
            finish = choice.get("finish_reason") or finish
        return {"text": text, "finish": finish, "calls": calls, "chunks": chunks}

    # -- replies ------------------------------------------------------------------

    def test_stub_echo_streams_ECHO_text_word_by_word(self) -> None:
        out = self.post(chat("e2e:echo hello there world"))
        self.assertEqual(out["text"], "ECHO hello there world")
        self.assertEqual(out["finish"], "stop")
        self.assertGreaterEqual(out["chunks"], 4)

    def test_stub_echo_non_stream_has_same_text(self) -> None:
        out = self.post(chat("e2e:echo hello there", stream=False))
        self.assertEqual((out["text"], out["finish"]), ("ECHO hello there", "stop"))

    def test_stub_plain_prompt_is_echoed(self) -> None:
        self.assertEqual(self.post(chat("just a normal question"))["text"], "ECHO just a normal question")

    def test_stub_long_has_headings_table_fence_path_and_all_links(self) -> None:
        out = self.post(chat("e2e:long"))
        text = out["text"]
        self.assertIn("\n## Part 1", text)
        self.assertIn("|---|---|---|", text)
        self.assertIn("```dart", text)
        self.assertTrue(any(len(line) > 1000 for line in text.splitlines() if "payload" in line))
        self.assertIn("lib/main.dart:42", text)
        for link in stub_model.LONG_LINKS:
            self.assertIn(f"]({link})", text)
        self.assertEqual(len(stub_model.LONG_URL), 3000)
        self.assertEqual(out["finish"], "stop")

    def test_stub_long_is_paced_for_about_60_seconds(self) -> None:
        reply = stub_model.plan(chat("e2e:long"))
        self.assertAlmostEqual(reply.nominal_seconds, 60.0, delta=1.0)

    def test_stub_echo_paced_at_20_words_per_second(self) -> None:
        reply = stub_model.plan(chat("e2e:echo " + " ".join(["w"] * 39)))
        self.assertAlmostEqual(reply.nominal_seconds, 2.0, delta=0.01)

    def test_stub_cut_finishes_with_length_stream_and_non_stream(self) -> None:
        self.assertEqual(self.post(chat("e2e:cut"))["finish"], "length")
        self.assertEqual(self.post(chat("e2e:cut", stream=False))["finish"], "length")

    def test_stub_secret_reply_contains_fake_key(self) -> None:
        self.assertIn("sk-e2e-FAKE-0123456789abcdef", self.post(chat("e2e:secret"))["text"])

    def test_stub_slow_finish_text_has_six_tasks_then_all_done_in_30s(self) -> None:
        reply = stub_model.plan(chat("e2e:slow-finish"))
        self.assertAlmostEqual(reply.nominal_seconds, 30.0, delta=0.5)
        self.assertEqual(reply.text.count("- [x] "), 6)
        self.assertTrue(reply.text.endswith("All done."))
        self.assertTrue(self.post(chat("e2e:slow-finish"))["text"].endswith("All done."))

    def test_stub_slow_finish_with_todo_tool_walks_six_tasks_then_all_done(self) -> None:
        after: list[dict[str, Any]] = []
        total = 0.0
        for rnd in range(7):
            reply = stub_model.plan(chat("e2e:slow-finish", OC2_TOOLS, after))
            total += reply.nominal_seconds
            self.assertEqual(reply.finish, "tool_calls")
            todos = reply.tool_calls[0]["arguments"]["todos"]
            self.assertEqual(len(todos), 6)
            self.assertEqual(sum(t["status"] == "completed" for t in todos), rnd)
            after += tool_round("todowrite")
        final = stub_model.plan(chat("e2e:slow-finish", OC2_TOOLS, after))
        self.assertEqual((final.text, final.finish), ("All done.", "stop"))
        self.assertAlmostEqual(total, 30.0, delta=0.5)

    def test_stub_ask_edit_calls_edit_tool_then_answers_after_result(self) -> None:
        out = self.post(chat("e2e:ask-edit", OC2_TOOLS))
        self.assertEqual(out["finish"], "tool_calls")
        call = out["calls"][0]
        self.assertEqual(call["name"], "edit")  # the schema-matching (filePath) edit shape
        self.assertEqual(call["arguments"]["filePath"], PROJECT + "/notes.txt")
        follow = self.post(chat("e2e:ask-edit", OC2_TOOLS, tool_round("edit")))
        self.assertEqual(follow["finish"], "stop")
        self.assertEqual(follow["calls"], [])
        self.assertIn("notes.txt", follow["text"])

    def test_stub_ask_edit_falls_back_to_verified_write_when_edit_absent(self) -> None:
        tools = [tool("write", ["path", "content"]), tool("shell", ["command"])]
        call = self.post(chat("e2e:ask-edit", tools, stream=False))["calls"][0]
        self.assertEqual(call["name"], "write")
        self.assertEqual(call["arguments"]["path"], PROJECT + "/notes.txt")
        self.assertIn("# Notes (edited)", call["arguments"]["content"])

    def test_stub_uses_project_dir_from_system_prompt(self) -> None:
        body = chat("e2e:ask-edit", [tool("write", ["path", "content"])], system="Working directory: /tmp/elsewhere")
        self.assertEqual(stub_model.plan(body).tool_calls[0]["arguments"]["path"], "/tmp/elsewhere/notes.txt")

    def test_stub_ask_shell_runs_given_command_with_shell_tool(self) -> None:
        out = self.post(chat("e2e:ask-shell ls -la", OC2_TOOLS))
        self.assertEqual(out["calls"], [{"name": "shell", "arguments": {"command": "ls -la"}}])
        self.assertEqual(self.post(chat("e2e:ask-shell ls -la", OC2_TOOLS, tool_round("shell")))["finish"], "stop")

    def test_stub_ask_shell_uses_bash_when_only_bash_is_offered(self) -> None:
        tools = [tool("bash", ["command", "description"])]
        call = self.post(chat("e2e:ask-shell git status", tools))["calls"][0]
        self.assertEqual(call["name"], "bash")
        self.assertEqual(call["arguments"]["command"], "git status")

    def test_stub_question_choice_choice4_option_counts(self) -> None:
        for prompt, count in (("e2e:question", 0), ("e2e:choice", 3), ("e2e:choice4", 4)):
            with self.subTest(prompt=prompt):
                out = self.post(chat(prompt, OC2_TOOLS))
                self.assertEqual(out["finish"], "tool_calls")
                questions = out["calls"][0]["arguments"]["questions"]
                self.assertEqual(len(questions), 1)
                self.assertEqual(len(questions[0]["options"]), count)
                self.assertEqual(self.post(chat(prompt, OC2_TOOLS, tool_round("question")))["finish"], "stop")

    def test_stub_files_edit_edits_two_different_files(self) -> None:
        out = self.post(chat("e2e:files-edit", OC2_TOOLS))
        paths = {c["arguments"].get("filePath") or c["arguments"].get("path") for c in out["calls"]}
        self.assertEqual(paths, {PROJECT + "/README.md", PROJECT + "/data.csv"})
        self.assertEqual(self.post(chat("e2e:files-edit", OC2_TOOLS, tool_round("edit", 2)))["text"], "Edited 2 files.")

    def test_stub_title_request_gets_fixed_title(self) -> None:
        body = chat("e2e:ask-edit", system="Generate a short title for this conversation.", stream=False)
        self.assertEqual(self.post(body)["text"], stub_model.TITLE)

    def test_stub_perf_stream_6000_is_fixed_6000_words_three_fences(self) -> None:
        text = stub_model.perf_stream_text()
        self.assertEqual(len(text.split()), 6000)
        self.assertEqual(text.count("```"), 6)
        self.assertIn("x400 = 400", text)
        self.assertIn("|---|---|---|", text)
        chunks = stub_model.perf_stream_chunks()
        self.assertTrue(all(1 <= len(c) <= 12 for c, _ in chunks))
        self.assertTrue(all(1 / 90 - 1e-9 <= d <= 1 / 40 + 1e-9 for _, d in chunks))
        out = self.post(chat("perf:stream-6000"), timeout=60)
        self.assertEqual(out["text"], text)
        self.assertEqual(out, self.post(chat("perf:stream-6000"), timeout=60))  # identical on every run

    def test_stub_perf_stream_short_is_a_15s_prefix(self) -> None:
        reply = stub_model.plan(chat("perf:stream-short"))
        self.assertLessEqual(reply.nominal_seconds, 15.0)
        self.assertGreater(reply.nominal_seconds, 14.5)
        self.assertTrue(stub_model.perf_stream_text().startswith(reply.text))

    def test_stub_perf_tools_200_makes_200_read_calls_then_text(self) -> None:
        after: list[dict[str, Any]] = []
        total = 0
        for _ in range(50):
            reply = stub_model.plan(chat("perf:tools-200", OC2_TOOLS, after))
            if not reply.tool_calls:
                break
            self.assertTrue(all(c["name"] == "read" for c in reply.tool_calls))
            total += len(reply.tool_calls)
            after += tool_round("read", len(reply.tool_calls))
        self.assertEqual(total, 200)
        self.assertEqual(reply.finish, "stop")

    def test_stub_perf_history_prompt_points_at_seeded_session(self) -> None:
        self.assertIn("seeded session", self.post(chat("perf:history-5000"))["text"])

    def test_stub_models_endpoint_lists_stub(self) -> None:
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        conn.request("GET", "/v1/models")
        data = json.loads(conn.getresponse().read())
        conn.close()
        self.assertEqual([m["id"] for m in data["data"]], ["stub"])

    def test_stub_log_records_command_name_never_prompt_text(self) -> None:
        self.post(chat(f"e2e:echo {SECRET_WORDS}"))
        self.post(chat(f"tell me about {SECRET_WORDS}"))
        deadline = time.monotonic() + 5
        commands: list[Any] = []
        while time.monotonic() < deadline:
            commands = [r.get("command") for r in self.srv.read_log()]
            if "e2e:echo" in commands and "other" in commands:
                break
            time.sleep(0.05)
        self.assertIn("e2e:echo", commands)
        self.assertIn("other", commands)
        self.assertNotIn(SECRET_WORDS, self.log.read_text())
        self.assertNotIn("ECHO", self.log.read_text())

    def test_stub_log_marks_client_abort_mid_stream(self) -> None:
        self.srv.speed = 1
        try:
            raw = json.dumps(chat("e2e:long")).encode()
            with socket.create_connection(("127.0.0.1", self.port), timeout=5) as sock:
                sock.sendall(b"POST /v1/chat/completions HTTP/1.1\r\nHost: x\r\nContent-Type: application/json\r\n"
                             b"Content-Length: %d\r\n\r\n%s" % (len(raw), raw))
                self.assertIn(b"200", sock.recv(4096))
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                if any(r.get("command") == "e2e:long" and r.get("aborted") for r in self.srv.read_log()):
                    break
                time.sleep(0.05)
            self.assertTrue(any(r.get("command") == "e2e:long" and r.get("aborted") for r in self.srv.read_log()))
        finally:
            self.srv.speed = 400

    def test_stub_speed_env_scales_pace(self) -> None:
        srv = stub_model.make_server(free_port())
        try:
            old = os.environ.get("FX_STUB_SPEED")
            os.environ["FX_STUB_SPEED"] = "10"
            self.assertEqual(srv.current_speed(), 10.0)
            os.environ.pop("FX_STUB_SPEED")
            self.assertEqual(srv.current_speed(), 1.0)
            srv.speed = 3
            self.assertEqual(srv.current_speed(), 3.0)
        finally:
            if old is not None:
                os.environ["FX_STUB_SPEED"] = old
            srv.server_close()
        self.srv.speed = 1
        try:
            start = time.monotonic()
            self.post(chat("e2e:echo " + " ".join(["w"] * 9)))  # 10 words at 20/s = 0.5 s
            slow = time.monotonic() - start
            self.srv.speed = 10
            start = time.monotonic()
            self.post(chat("e2e:echo " + " ".join(["w"] * 9)))
            fast = time.monotonic() - start
        finally:
            self.srv.speed = 400
        self.assertGreater(slow, 0.4)
        self.assertLess(fast, 0.25)

    def test_stub_seed_files_match_edit_shapes(self) -> None:
        self.assertGreaterEqual(len(stub_model.MAIN_DART.splitlines()), 42)
        self.assertIn("# Notes", stub_model.NOTES_TXT)
        self.assertIn("3,cherry,7", stub_model.DATA_CSV)
        self.assertIn("# App", stub_model.README_MD)


class StubModelCliTest(unittest.TestCase):
    def test_stub_runs_standalone_isolated_and_stops_by_pid(self) -> None:
        tmp = Path(tempfile.mkdtemp(prefix="fx-stub-cli-"))
        port = free_port()
        log = tmp / "log.jsonl"
        proc = subprocess.Popen([sys.executable, "-I", str(SERVERS / "stub_model.py"), "--port", str(port),
                                 "--log", str(log), "--speed", "100"], stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL, cwd=str(tmp))
        try:
            self.assertTrue(wait_for_port(port, timeout=10))
            conn = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
            conn.request("POST", "/v1/chat/completions", json.dumps(chat("e2e:secret", stream=False)),
                         {"Content-Type": "application/json"})
            data = json.loads(conn.getresponse().read())
            conn.close()
            self.assertIn(stub_model.FAKE_KEY, data["choices"][0]["message"]["content"])
            deadline = time.monotonic() + 5
            while not (log.exists() and log.read_text().strip()) and time.monotonic() < deadline:
                time.sleep(0.05)
            self.assertEqual(json.loads(log.read_text().splitlines()[-1])["command"], "e2e:secret")
        finally:
            os.kill(proc.pid, signal.SIGTERM)  # exact PID
            try:
                proc.wait(5)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait(5)
            shutil.rmtree(tmp, ignore_errors=True)
        self.assertIsNotNone(proc.returncode)


if __name__ == "__main__":
    unittest.main()
