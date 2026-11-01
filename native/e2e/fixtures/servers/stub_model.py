#!/usr/bin/env python3
"""stub_model.py - deterministic OpenAI-compatible model for FX-OC2 (plan 14 §4.4).

Extends the approach of tool/qa/issue87/mock_llm.py. Python 3.10+ stdlib only.

    python3 -I stub_model.py --port 4299 [--log FILE] [--dir /abs/project] [--speed 10]

or, inside a fixture::

    srv = stub_model.make_server(port, log_path=..., default_dir=...)
    threading.Thread(target=srv.serve_forever, daemon=True).start()   # or start_in_thread()

Endpoints (binds 127.0.0.1 only):
    POST /v1/chat/completions   stream (SSE ``data:`` chunks, then ``data: [DONE]``) or not
    GET  /v1/models             one model, ``stub``
    GET  /health                {"ok": true}

Prompts (last user message; e2e-flows.md "Stub model magic prompts"):
    e2e:echo <text>      ``ECHO <text>`` at ~20 words/s
    e2e:long             ~60 s: headings, a table, a fenced block with a very long
                         line, ``lib/main.dart:42`` and the LONG_LINKS
    e2e:cut              text, then finish_reason ``length``
    e2e:slow-finish      ~30 s; a 6-item task list (todo tool when offered, else a
                         markdown task list), then ``All done.``
    e2e:ask-edit         tool call editing notes.txt; after the tool result a short text
    e2e:ask-shell <cmd>  shell tool call running <cmd> (default ``ls``)
    e2e:question         question tool, one free-text question
    e2e:choice           question tool, 3 options
    e2e:choice4          question tool, 4 options
    e2e:secret           a reply containing FAKE_KEY
    e2e:files-edit       two edit tool calls (README.md and data.csv)
    perf:stream-6000     plan 09 §14.3: a fixed 6,000-word reply (3 fenced blocks,
                         one 400 lines; a table; two lists) as 1-12 character deltas
                         at a seeded, jittered 40-90 deltas/s (~85 s at speed 1)
    perf:stream-short    plan 13: the first 15 s of perf:stream-6000
    perf:tools-200       plan 09 §14.3: one turn with 200 read tool calls, in steps of
                         PERF_TOOLS_BATCH, then a short text
    perf:history-5000    is a seeded *session* (fx_oc2 ``--sessions perf-history``);
                         as a prompt it answers a fixed sentence saying so
    anything else        ``ECHO <the prompt>``
Title requests (system prompt asks for a title [U]) get TITLE.

After a tool call, OpenCode sends the tool result back; the "round" is the number of
assistant tool-call messages after the last user message, and each command decides
what the next round answers (normally a short final text).

Pace: every delay is divided by the speed: env FX_STUB_SPEED (read per request,
default 1; 10 = ten times faster), unless the server's ``speed`` attribute is set
(fx_oc2's ``model-speed`` control action, or ``--speed``).

Tool shapes are data (TOOL_SHAPES). Each kind lists candidates in priority order; when
the request offers tools, the first candidate whose name is offered and whose args
satisfy that tool's JSON schema (required keys present, no unknown keys) wins, so a
wrong guess falls through to a verified shape. Shapes marked [U] are unverified.

The request log (JSON lines) records metadata only: path, status, the matched command
name (e.g. ``e2e:echo``, ``title``, ``other``), stream flag, round, finish reason,
number of tools offered, whether the client aborted. Never prompt or reply text.
"""

from __future__ import annotations

import argparse
import functools
import itertools
import json
import os
import random
import re
import sys
import threading
import time
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

LOOPBACK = "127.0.0.1"
MODEL_ID = "stub"
TITLE = "E2E conversation"
FAKE_KEY = "sk-e2e-FAKE-0123456789abcdef"

# --------------------------------------------------------------------------- seeded files
# fx_oc2 seeds project `app` with exactly these, so edit shapes can match them.

README_MD = "# App\n\nA small project for the end-to-end fixtures.\n\nSee notes.txt for the to-do list.\n"
NOTES_TXT = "# Notes\n\n- [ ] water the plants\n- [ ] call the bank\n"
DATA_CSV = "id,name,qty\n1,apple,3\n2,banana,5\n3,cherry,7\n"


def _main_dart() -> str:
    lines = [
        "// Fixture app for the end-to-end flows.",
        "import 'dart:math';",
        "",
        "void main() {",
        "  final app = FixtureApp();",
        "  app.run();",
        "}",
        "",
        "class FixtureApp {",
        "  final List<String> items = <String>[];",
        "",
    ]
    n = 1
    while len(lines) < 40:
        lines.append(f"  int step{n}(int x) => x + {n};")
        n += 1
    lines += [
        "",
        "  void run() {  // line 42: the e2e:long reply links here",
        "    for (var i = 0; i < 3; i++) {",
        "      items.add('item $i');",
        "    }",
        "    print(items.join(', '));",
        "    print(sqrt(16));",
        "  }",
        "}",
    ]
    return "\n".join(lines) + "\n"


MAIN_DART = _main_dart()
assert MAIN_DART.splitlines()[41].startswith("  void run()"), "line 42 must be run()"

NOTES_EDITED = NOTES_TXT.replace("# Notes", "# Notes (edited)", 1)
README_EDITED = README_MD.replace("# App", "# App (edited)", 1)
DATA_EDITED = DATA_CSV + "4,date,2\n"

# --------------------------------------------------------------------------- tool shapes
# Placeholders: {dir} = the project directory (absolute; dropped with its slash when
# unknown), {cmd} = the ask-shell command. Verified sources:
#   OC2 2.0.29 tool names/args: docs/qa/aiteam-oc2-probe-2026-10-14/provider-results.json
#   (shell {command}, write {path, content}, read {path}, glob {pattern}) and
#   tool/qa/relswitch_permission_probe.py.

TOOL_SHAPES: dict[str, list[dict[str, Any]]] = {
    "edit-notes": [
        # [U] OC2 `edit` args are not captured anywhere in the repo; path-style guess.
        {"name": "edit", "args": {"path": "{dir}/notes.txt", "oldString": "# Notes", "newString": "# Notes (edited)"}},
        # [U] OpenCode v1 edit shape (filePath/oldString/newString), as lib/ reads it.
        {"name": "edit", "args": {"filePath": "{dir}/notes.txt", "oldString": "# Notes", "newString": "# Notes (edited)"}},
        # verified OC2 2.0.29: write {path, content}
        {"name": "write", "args": {"path": "{dir}/notes.txt", "content": NOTES_EDITED}},
    ],
    "edit-readme": [
        {"name": "edit", "args": {"path": "{dir}/README.md", "oldString": "# App", "newString": "# App (edited)"}},  # [U]
        {"name": "edit", "args": {"filePath": "{dir}/README.md", "oldString": "# App", "newString": "# App (edited)"}},  # [U]
        {"name": "write", "args": {"path": "{dir}/README.md", "content": README_EDITED}},
    ],
    "edit-data": [
        {"name": "edit", "args": {"path": "{dir}/data.csv", "oldString": "3,cherry,7", "newString": "3,cherry,7\n4,date,2"}},  # [U]
        {"name": "edit", "args": {"filePath": "{dir}/data.csv", "oldString": "3,cherry,7", "newString": "3,cherry,7\n4,date,2"}},  # [U]
        {"name": "write", "args": {"path": "{dir}/data.csv", "content": DATA_EDITED}},
    ],
    "shell": [
        {"name": "shell", "args": {"command": "{cmd}"}},  # verified OC2 2.0.29
        {"name": "bash", "args": {"command": "{cmd}", "description": "Run {cmd}"}},  # [U] OpenCode v1 bash
    ],
    # [U] all question shapes: OC2 turned questions into forms; the tool the model calls
    # to raise one is not captured. This is OpenCode v1's `question` tool input, which
    # lib/ui/widgets/tool_card_contract.dart reads (input.questions).
    "question": [
        {"name": "question", "args": {"questions": [
            {"question": "What should the release be called?", "header": "Release name", "options": []}]}},
    ],
    "choice": [
        {"name": "question", "args": {"questions": [
            {"question": "Which size should the build be?", "header": "Build size", "options": [
                {"label": "Small", "description": "Fastest build"},
                {"label": "Medium", "description": "The usual build"},
                {"label": "Large", "description": "Everything included"}]}]}},
    ],
    "choice4": [
        {"name": "question", "args": {"questions": [
            {"question": "Which branch should I use?", "header": "Branch", "options": [
                {"label": "main", "description": "The default branch"},
                {"label": "develop", "description": "Work in progress"},
                {"label": "release", "description": "The next release"},
                {"label": "hotfix", "description": "Urgent fixes"}]}]}},
    ],
    # [U] todo tool name and args (OpenCode v1 todowrite); used only when offered.
    "todo": [
        {"name": "todowrite", "args": {"todos": "{todos}"}},
    ],
    "read": [
        {"name": "read", "args": {"path": "{dir}/README.md"}},  # verified OC2 2.0.29
        {"name": "read", "args": {"filePath": "{dir}/README.md"}},  # [U] v1
    ],
}

PERF_TOOLS_TOTAL = 200
PERF_TOOLS_BATCH = 20
SLOW_FINISH_SECONDS = 30.0
LONG_SECONDS = 60.0
ECHO_WORDS_PER_SECOND = 20.0
SLOW_TASKS = ["Read the notes", "Check the data file", "Update the README", "Run the tests",
              "Fix the failing test", "Write the summary"]

# Where the project directory may appear in OpenCode's system prompt [U].
DIR_PATTERNS = [r"[Ww]orking directory:\s*(/\S+)", r"<cwd>\s*(/[^<\s]+)", r'"directory"\s*:\s*"(/[^"]+)"']

# --------------------------------------------------------------------------- long reply

LONG_URL = "https://example.com/long/" + "a" * (3000 - len("https://example.com/long/"))
assert len(LONG_URL) == 3000
LONG_LINKS = ["https://example.com/a", "http://example.org/b", "javascript:alert(1)", "intent://x",
              "https://user:pw@example.com/", LONG_URL]
LONG_FENCE_LINE = "const payload = '" + "0123456789abcdef" * 120 + "';"


@functools.lru_cache(maxsize=None)
def long_text() -> str:
    para = ("The fixture keeps every reply the same on every run so that a flow can wait for a "
            "known word instead of guessing. This sentence is here to give the reply some body, "
            "and the next one says which part of the reply you are reading now.")
    parts = ["# A long reply", ""]
    for i in range(1, 7):
        parts += [f"## Part {i}", "", f"{para} You are reading part {i}.", ""]
        if i == 2:
            parts += ["| File | Lines | Status |", "|---|---|---|", "| README.md | 5 | clean |",
                      "| notes.txt | 4 | edited |", "| lib/main.dart | 50 | clean |", ""]
        if i == 3:
            parts += ["```dart", "void main() {", f"  {LONG_FENCE_LINE}", "}", "```", ""]
        if i == 4:
            parts += ["The run method starts at lib/main.dart:42 in the app project.", ""]
        if i == 5:
            parts += ["Links to try:", "",
                      "- [a safe page](https://example.com/a)",
                      "- [a plain page](http://example.org/b)",
                      "- [a script](javascript:alert(1))",
                      "- [an intent](intent://x)",
                      "- [a page with a password](https://user:pw@example.com/)",
                      f"- [a very long address]({LONG_URL})", ""]
    words = len(" ".join(parts).split())
    filler = (para + " ").split()
    extra: list[str] = []
    i = 0
    while words + len(extra) < 1200:
        extra.append(filler[i % len(filler)])
        i += 1
    parts += ["## Closing", "", " ".join(extra), "", "That is the end of the long reply."]
    return "\n".join(parts) + "\n"


# --------------------------------------------------------------------------- perf stream


@functools.lru_cache(maxsize=None)
def perf_stream_text(total_words: int = 6000) -> str:
    rng = random.Random(6000)
    vocab = ("the agent reads a file and writes a short plan before it changes any line of code "
             "so the person can follow each step on a small screen while the reply streams").split()
    blocks: list[str] = ["# Performance stream"]
    blocks.append("- " + "\n- ".join(f"first list item {i}" for i in range(1, 9)))
    blocks.append("1. " + "\n1. ".join(f"second list item {i}" for i in range(1, 9)))
    blocks.append("| Name | Value | Note |\n|---|---|---|\n" +
                  "\n".join(f"| row{i} | {i * 7} | ok |" for i in range(1, 11)))
    blocks.append("```python\n" + "\n".join(f"x{i} = {i}" for i in range(1, 401)) + "\n```")
    blocks.append("```dart\n" + "\n".join(f"final v{i} = {i};" for i in range(1, 21)) + "\n```")
    blocks.append("```sh\n" + "\n".join(f"echo step{i}" for i in range(1, 11)) + "\n```")
    fixed = sum(len(b.split()) for b in blocks)
    remaining = total_words - fixed
    paragraphs = 24
    out = [blocks[0]]
    structure = blocks[1:]
    for p in range(paragraphs):
        n = remaining // paragraphs + (1 if p < remaining % paragraphs else 0)
        out.append(" ".join(rng.choice(vocab) for _ in range(n)) + ".")
        if p % 4 == 3 and structure:
            out.append(structure.pop(0))
    out += structure
    text = "\n\n".join(out) + "\n"
    assert len(text.split()) == total_words, len(text.split())
    return text


@functools.lru_cache(maxsize=None)
def perf_stream_chunks() -> tuple[tuple[str, float], ...]:
    text = perf_stream_text()
    rng = random.Random(6001)
    chunks = []
    i = 0
    while i < len(text):
        size = rng.randint(1, 12)
        chunks.append((text[i:i + size], 1.0 / rng.uniform(40, 90)))
        i += size
    return tuple(chunks)


# --------------------------------------------------------------------------- planning


@dataclass
class Reply:
    command: str
    chunks: list[tuple[str, float]] = field(default_factory=list)   # (delta, seconds before it at speed 1)
    tool_calls: list[dict[str, Any]] = field(default_factory=list)  # {"name", "arguments": dict}
    finish: str = "stop"
    round: int = 0

    @property
    def text(self) -> str:
        return "".join(c for c, _ in self.chunks)

    @property
    def nominal_seconds(self) -> float:
        return sum(d for _, d in self.chunks)


def paced_words(text: str, seconds_total: float | None = None, per_word: float | None = None) -> list[tuple[str, float]]:
    tokens = re.findall(r"\s*\S+\s*", text) or [text]
    if per_word is None:
        per_word = (seconds_total or 0.0) / max(len(tokens), 1)
    return [(t, per_word) for t in tokens]


def _text_of(message: dict[str, Any]) -> str:
    content = message.get("content")
    if isinstance(content, list):
        return " ".join(p.get("text", "") for p in content if isinstance(p, dict))
    return content if isinstance(content, str) else ""


def _offered_tools(body: dict[str, Any]) -> dict[str, dict[str, Any]]:
    offered: dict[str, dict[str, Any]] = {}
    for tool in body.get("tools") or []:
        if not isinstance(tool, dict):
            continue
        fn = tool.get("function") if isinstance(tool.get("function"), dict) else tool
        name = fn.get("name")
        if isinstance(name, str):
            params = fn.get("parameters")
            offered[name] = params if isinstance(params, dict) else {}
    return offered


def _fits(shape: dict[str, Any], offered: dict[str, dict[str, Any]]) -> bool:
    if shape["name"] not in offered:
        return False
    schema = offered[shape["name"]]
    args = shape["args"]
    if any(key not in args for key in schema.get("required") or []):
        return False
    props = schema.get("properties")
    if isinstance(props, dict) and props and any(key not in props for key in args):
        return False
    return True


def choose_shape(kind: str, offered: dict[str, dict[str, Any]]) -> dict[str, Any]:
    candidates = TOOL_SHAPES[kind]
    if not offered:
        return candidates[0]
    for shape in candidates:
        if _fits(shape, offered):
            return shape
    for shape in candidates:
        if shape["name"] in offered:
            return shape
    return candidates[0]


def _fill(value: Any, subs: dict[str, Any]) -> Any:
    if isinstance(value, str):
        for key, sub in subs.items():
            token = "{" + key + "}"
            if value == token and not isinstance(sub, str):
                return sub
            if key == "dir" and not sub:
                value = value.replace("{dir}/", "").replace("{dir}", ".")
            elif isinstance(sub, str):
                value = value.replace(token, sub)
        return value
    if isinstance(value, list):
        return [_fill(v, subs) for v in value]
    if isinstance(value, dict):
        return {k: _fill(v, subs) for k, v in value.items()}
    return value


def tool_call(kind: str, offered: dict[str, dict[str, Any]], **subs: Any) -> dict[str, Any]:
    shape = choose_shape(kind, offered)
    return {"name": shape["name"], "arguments": _fill(shape["args"], subs)}


def _project_dir(system: str, default_dir: str | None) -> str | None:
    for pattern in DIR_PATTERNS:
        match = re.search(pattern, system)
        if match:
            return match.group(1).rstrip("/")
    return default_dir.rstrip("/") if default_dir else None


COMMAND_RE = re.compile(r"(?:^|\s)((?:e2e|perf):[A-Za-z0-9_-]+)[ \t]*(.*)", re.S)
KNOWN = {"e2e:echo", "e2e:long", "e2e:cut", "e2e:slow-finish", "e2e:ask-edit", "e2e:ask-shell",
         "e2e:question", "e2e:choice", "e2e:choice4", "e2e:secret", "e2e:files-edit",
         "perf:stream-6000", "perf:stream-short", "perf:tools-200", "perf:history-5000"}


def is_title_request(body: dict[str, Any]) -> bool:
    """[U] OpenCode's title agent: a system prompt about generating a title, no tools."""
    msgs = body.get("messages") or []
    system = " ".join(_text_of(m) for m in msgs if m.get("role") == "system").lower()
    return "title" in system and ("generate" in system or not body.get("tools"))


def plan(body: dict[str, Any], default_dir: str | None = None) -> Reply:
    """What to answer for one chat-completions request (pure; no sleeping)."""
    msgs = [m for m in body.get("messages") or [] if isinstance(m, dict)]
    if is_title_request(body):
        return Reply("title", [(TITLE, 0.0)])
    last_user = max((i for i, m in enumerate(msgs) if m.get("role") == "user"), default=-1)
    prompt = _text_of(msgs[last_user]) if last_user >= 0 else ""
    after = msgs[last_user + 1:] if last_user >= 0 else []
    rnd = sum(1 for m in after if m.get("role") == "assistant" and m.get("tool_calls"))
    system = " ".join(_text_of(m) for m in msgs if m.get("role") == "system")
    offered = _offered_tools(body)
    pdir = _project_dir(system, default_dir)

    match = COMMAND_RE.search(prompt)
    command = match.group(1) if match else "other"
    rest = match.group(2).strip() if match else ""
    if match and command not in KNOWN:
        command = "e2e:unknown" if command.startswith("e2e:") else "perf:unknown"
    reply = Reply(command, round=rnd)
    words = functools.partial(paced_words, per_word=1.0 / ECHO_WORDS_PER_SECOND)

    def calls(*items: dict[str, Any]) -> Reply:
        reply.tool_calls = list(items)
        reply.finish = "tool_calls"
        return reply

    if command == "e2e:echo":
        reply.chunks = words(f"ECHO {rest}".rstrip())
    elif command == "e2e:long":
        reply.chunks = paced_words(long_text(), seconds_total=LONG_SECONDS)
    elif command == "e2e:cut":
        reply.chunks = words("This reply is going to stop in the middle because the length limit "
                             "is reached before the sentence can")
        reply.finish = "length"
    elif command == "e2e:secret":
        reply.chunks = words(f"Here is the key you asked for: {FAKE_KEY} - keep it out of logs.")
    elif command == "e2e:slow-finish":
        steps = len(SLOW_TASKS) + 1
        per_step = SLOW_FINISH_SECONDS / steps
        if "todo" in TOOL_SHAPES and any(_fits(s, offered) for s in TOOL_SHAPES["todo"]):
            if rnd < steps:
                todos = [{"id": str(i + 1), "content": task, "priority": "medium",
                          "status": "completed" if i < rnd else ("in_progress" if i == rnd else "pending")}
                         for i, task in enumerate(SLOW_TASKS)]
                label = f"Working on task {min(rnd + 1, len(SLOW_TASKS))} of {len(SLOW_TASKS)}."
                if rnd == len(SLOW_TASKS):
                    label = "Checking the finished tasks."
                reply.chunks = paced_words(label, seconds_total=per_step)
                return calls(tool_call("todo", offered, dir=pdir, todos=todos))
            reply.chunks = words("All done.")
        else:
            text = "Tasks:\n\n" + "".join(f"- [x] {t}\n" for t in SLOW_TASKS)
            lines = text.splitlines(keepends=True)
            reply.chunks = [(line, per_step if i >= 2 else 0.0) for i, line in enumerate(lines)]
            reply.chunks += [("\nAll done.", per_step)]
    elif command == "e2e:ask-edit":
        if rnd == 0:
            reply.chunks = words("I will edit notes.txt.")
            return calls(tool_call("edit-notes", offered, dir=pdir))
        reply.chunks = words("Finished with notes.txt.")
    elif command == "e2e:ask-shell":
        cmd = rest.splitlines()[0].strip() if rest else "ls"
        if rnd == 0:
            reply.chunks = words("I will run a command.")
            return calls(tool_call("shell", offered, dir=pdir, cmd=cmd))
        reply.chunks = words("The command finished.")
    elif command in ("e2e:question", "e2e:choice", "e2e:choice4"):
        if rnd == 0:
            reply.chunks = words("I have a question for you.")
            return calls(tool_call(command.split(":")[1], offered, dir=pdir))
        reply.chunks = words("Thanks for the answer.")
    elif command == "e2e:files-edit":
        if rnd == 0:
            reply.chunks = words("I will edit two files.")
            return calls(tool_call("edit-readme", offered, dir=pdir), tool_call("edit-data", offered, dir=pdir))
        reply.chunks = words("Edited 2 files.")
    elif command == "perf:stream-6000":
        reply.chunks = list(perf_stream_chunks())
    elif command == "perf:stream-short":
        out, total = [], 0.0
        for chunk in perf_stream_chunks():
            if total + chunk[1] > 15.0:
                break
            out.append(chunk)
            total += chunk[1]
        reply.chunks = out
    elif command == "perf:tools-200":
        done = rnd * PERF_TOOLS_BATCH
        if done < PERF_TOOLS_TOTAL:
            n = min(PERF_TOOLS_BATCH, PERF_TOOLS_TOTAL - done)
            reply.chunks = [(f"Reading, batch {rnd + 1}.", 0.0)]
            return calls(*[tool_call("read", offered, dir=pdir) for _ in range(n)])
        reply.chunks = words(f"Read README.md {PERF_TOOLS_TOTAL} times.")
    elif command == "perf:history-5000":
        reply.chunks = words("perf:history-5000 is a seeded session: start FX-OC2 with sessions perf-history and open it.")
    else:
        reply.chunks = words(f"ECHO {prompt.strip()}".rstrip())
    return reply


# --------------------------------------------------------------------------- server

_ids = itertools.count(1)


class StubServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, port: int, log_path: str | Path | None = None, default_dir: str | None = None,
                 speed: float | None = None) -> None:
        self.log_path = Path(log_path) if log_path else None
        self.default_dir = default_dir
        self.speed = speed
        self._log_lock = threading.Lock()
        super().__init__((LOOPBACK, port), StubHandler)

    def current_speed(self) -> float:
        if self.speed:
            return float(self.speed)
        try:
            value = float(os.environ.get("FX_STUB_SPEED") or 1)
        except ValueError:
            value = 1.0
        return value if value > 0 else 1.0

    def log(self, **entry: Any) -> None:
        if not self.log_path:
            return
        entry.setdefault("t", round(time.time(), 3))
        with self._log_lock:
            self.log_path.parent.mkdir(parents=True, exist_ok=True)
            with self.log_path.open("a", encoding="utf-8") as handle:
                handle.write(json.dumps(entry, sort_keys=True) + "\n")

    def read_log(self) -> list[dict[str, Any]]:
        if not self.log_path or not self.log_path.exists():
            return []
        with self._log_lock:
            lines = self.log_path.read_text(encoding="utf-8").splitlines()
        return [json.loads(line) for line in lines if line.strip()]

    def clear_log(self) -> None:
        if self.log_path:
            with self._log_lock:
                self.log_path.write_text("", encoding="utf-8")


class StubHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "fx-stub"
    sys_version = ""
    server: StubServer

    def log_message(self, fmt: str, *args: Any) -> None:
        return

    def _json(self, obj: Any, status: int = 200) -> None:
        raw = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self) -> None:  # noqa: N802
        path = urlsplit(self.path).path.rstrip("/")
        if path.endswith("/models"):
            self._json({"object": "list", "data": [{"id": MODEL_ID, "object": "model", "owned_by": "fx"}]})
            status = 200
        elif path.endswith("/health"):
            self._json({"ok": True})
            status = 200
        else:
            self._json({"error": "not found"}, 404)
            status = 404
        self.server.log(method="GET", path=path, status=status)

    def do_POST(self) -> None:  # noqa: N802
        path = urlsplit(self.path).path.rstrip("/")
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b""
        if not path.endswith("/chat/completions"):
            self._json({"error": "not found"}, 404)
            self.server.log(method="POST", path=path, status=404)
            return
        try:
            body = json.loads(raw or b"{}")
            assert isinstance(body, dict)
        except (ValueError, AssertionError):
            self._json({"error": {"message": "invalid JSON body"}}, 400)
            self.server.log(method="POST", path=path, status=400)
            return
        reply = plan(body, self.server.default_dir)
        stream = bool(body.get("stream"))
        aborted = False
        try:
            if stream:
                self._stream(reply)
            else:
                self._complete(reply)
        except (BrokenPipeError, ConnectionResetError):
            aborted = True
            self.close_connection = True
        self.server.log(method="POST", path=path, status=200, command=reply.command, stream=stream,
                        round=reply.round, finish=reply.finish, tool_calls=len(reply.tool_calls),
                        tools_offered=len(body.get("tools") or []), aborted=aborted)

    def _usage(self, reply: Reply) -> dict[str, int]:
        out = max(1, len(reply.text) // 4)
        return {"prompt_tokens": 10, "completion_tokens": out, "total_tokens": 10 + out}

    def _calls(self, reply: Reply) -> list[dict[str, Any]]:
        return [{"index": i, "id": f"call_fx{next(_ids):06d}", "type": "function",
                 "function": {"name": c["name"], "arguments": json.dumps(c["arguments"])}}
                for i, c in enumerate(reply.tool_calls)]

    def _complete(self, reply: Reply) -> None:
        message: dict[str, Any] = {"role": "assistant", "content": reply.text}
        if reply.tool_calls:
            message["tool_calls"] = [{k: v for k, v in c.items() if k != "index"} for c in self._calls(reply)]
        self._json({"id": f"chatcmpl-fx-{next(_ids)}", "object": "chat.completion", "created": int(time.time()),
                    "model": MODEL_ID, "choices": [{"index": 0, "finish_reason": reply.finish, "message": message}],
                    "usage": self._usage(reply)})

    def _stream(self, reply: Reply) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        base = {"id": f"chatcmpl-fx-{next(_ids)}", "object": "chat.completion.chunk",
                "created": int(time.time()), "model": MODEL_ID}

        def send(choice: dict[str, Any], **extra: Any) -> None:
            self.wfile.write(b"data: " + json.dumps({**base, "choices": [choice], **extra}).encode() + b"\n\n")
            self.wfile.flush()

        speed = self.server.current_speed()
        send({"index": 0, "delta": {"role": "assistant", "content": ""}, "finish_reason": None})
        for delta, delay in reply.chunks:
            if delay > 0:
                time.sleep(delay / speed)
            send({"index": 0, "delta": {"content": delta}, "finish_reason": None})
        if reply.tool_calls:
            send({"index": 0, "delta": {"tool_calls": self._calls(reply)}, "finish_reason": None})
        send({"index": 0, "delta": {}, "finish_reason": reply.finish}, usage=self._usage(reply))
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()


def make_server(port: int, log_path: str | Path | None = None, default_dir: str | None = None,
                speed: float | None = None) -> StubServer:
    return StubServer(port, log_path=log_path, default_dir=default_dir, speed=speed)


def start_in_thread(port: int, **kw: Any) -> StubServer:
    srv = make_server(port, **kw)
    threading.Thread(target=srv.serve_forever, name="stub-model", daemon=True).start()
    return srv


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Deterministic OpenAI-compatible stub model (FX-OC2).")
    parser.add_argument("--port", type=int, default=4299)
    parser.add_argument("--log", help="JSON-lines request log (metadata only)")
    parser.add_argument("--dir", help="project directory used in tool-call paths")
    parser.add_argument("--speed", type=float, help="pace multiplier (overrides FX_STUB_SPEED)")
    args = parser.parse_args(argv)
    srv = make_server(args.port, log_path=args.log, default_dir=args.dir, speed=args.speed)
    print(f"stub model on http://{LOOPBACK}:{args.port}/v1", flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()
    return 0


if __name__ == "__main__":
    import signal

    def _on_term(*_: Any) -> None:
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, _on_term)
    sys.exit(main())
