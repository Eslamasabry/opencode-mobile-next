#!/usr/bin/env python3
"""Test stand-in for `claude` (emulator proof only; never shipped).

Speaks the stream-json control protocol the Claude Agent SDK uses, shaped on
OpenCode's recorded fixtures (packages/core/test/plugin/provider/claude-agent).
One turn: calls OpenCode's `bash` tool (`ls`) through the SDK's MCP bridge,
then answers with text naming what it saw. No network, no credentials.

FAKE_CLAUDE_LOGGED_OUT=1 (or a file `logged_out` next to this script) answers
the login probe as signed out.
"""
import json
import os
import sys
import threading
import uuid

HERE = os.path.dirname(os.path.realpath(__file__))
STARTUP = json.load(open(os.path.join(HERE, "startup.json")))
LOGGED_OUT = os.environ.get("FAKE_CLAUDE_LOGGED_OUT") == "1" or os.path.exists(
    os.path.join(HERE, "logged_out")
)
if "--version" in sys.argv[1:2]:
    print("2.1.316 (Claude Code)")
    sys.exit(0)

lock = threading.Lock()
pending = {}
state = {"hooks": {}, "tools": [], "session": None, "model": "claude-opus-5-5"}
for arg in sys.argv[1:]:
    for prefix in ("--session-id=", "--resume="):
        if arg.startswith(prefix):
            state["session"] = arg[len(prefix):]
    if arg.startswith("--model="):
        state["model"] = arg[len("--model="):]
if state["session"] is None:
    state["session"] = str(uuid.uuid4())
log = open(os.path.join(HERE, "fake.log"), "a") if os.access(HERE, os.W_OK) else None


def emit(message):
    with lock:
        sys.stdout.write(json.dumps(message) + "\n")
        sys.stdout.flush()
    if log:
        log.write("> " + json.dumps(message)[:300] + "\n")
        log.flush()


def base(extra):
    extra.setdefault("session_id", state["session"])
    extra.setdefault("uuid", str(uuid.uuid4()))
    return extra


def request(subtype, body):
    rid = str(uuid.uuid4())
    event = threading.Event()
    pending[rid] = {"event": event, "response": None}
    emit({"type": "control_request", "request_id": rid, "request": dict(body, subtype=subtype)})
    event.wait(600)
    return (pending.pop(rid) or {}).get("response")


def stream(event, msg_id):
    emit(base({"type": "stream_event", "event": event, "parent_tool_use_id": None, "api_message_id": msg_id}))


def usage():
    return {"input_tokens": 12, "cache_creation_input_tokens": 0, "cache_read_input_tokens": 0, "output_tokens": 20}


def assistant(msg_id, content):
    emit(base({"type": "assistant", "parent_tool_use_id": None, "message": {
        "model": state["model"], "id": msg_id, "type": "message", "role": "assistant",
        "content": content, "stop_reason": None, "stop_sequence": None, "usage": usage()}}))


def hook(event_name, tool_name, tool_input, tool_use_id, extra=None):
    ids = []
    for matcher in state["hooks"].get(event_name, []):
        ids.extend(matcher.get("hookCallbackIds", []))
    for callback in ids:
        data = {"session_id": state["session"], "cwd": os.getcwd(), "hook_event_name": event_name,
                "tool_name": tool_name, "tool_input": tool_input, "tool_use_id": tool_use_id,
                "permission_mode": "dontAsk"}
        data.update(extra or {})
        request("hook_callback", {"callback_id": callback, "input": data, "tool_use_id": tool_use_id})


def pick_tool():
    names = [t.get("name") for t in state["tools"]]
    for tool in state["tools"]:
        if tool.get("name") in ("shell", "bash"):
            props = ((tool.get("inputSchema") or {}).get("properties") or {})
            args = {"command": "ls"}
            if "description" in props:
                args["description"] = "List the project's files"
            return tool["name"], args
    for name, args in (
                       ("glob", {"pattern": "*"}),
                       ("list", {"path": "."})):
        if name in names:
            return name, args
    return None, None


def turn(user_message):
    emit(base({"type": "system", "subtype": "init", "cwd": os.getcwd(),
               "tools": ["mcp__opencode__" + (t.get("name") or "") for t in state["tools"]],
               "mcp_servers": [{"name": "opencode", "status": "connected", "source": "sdk"}],
               "model": state["model"], "permissionMode": "dontAsk", "slash_commands": [],
               "apiKeySource": "none", "claude_code_version": "2.1.316", "output_style": "default",
               "agents": [], "skills": [], "plugins": [], "capabilities": []}))
    name, args = pick_tool()
    seen = "no tool was offered"
    if name:
        msg_id = "msg_fake_" + uuid.uuid4().hex[:12]
        tool_id = "toolu_fake_" + uuid.uuid4().hex[:12]
        stream({"type": "message_start", "message": {"model": state["model"], "id": msg_id, "type": "message",
                "role": "assistant", "content": [], "stop_reason": None, "usage": usage()}}, msg_id)
        stream({"type": "content_block_start", "index": 0, "content_block": {
            "type": "tool_use", "id": tool_id, "name": "mcp__opencode__" + name, "input": {}}}, msg_id)
        stream({"type": "content_block_delta", "index": 0, "delta": {
            "type": "input_json_delta", "partial_json": json.dumps(args)}}, msg_id)
        assistant(msg_id, [{"type": "tool_use", "id": tool_id, "name": "mcp__opencode__" + name, "input": args}])
        hook("PreToolUse", "mcp__opencode__" + name, args, tool_id)
        stream({"type": "content_block_stop", "index": 0}, msg_id)
        stream({"type": "message_delta", "delta": {"stop_reason": "tool_use", "stop_sequence": None},
                "usage": usage()}, msg_id)
        stream({"type": "message_stop"}, msg_id)
        reply = request("mcp_message", {"server_name": "opencode", "message": {
            "jsonrpc": "2.0", "id": 2, "method": "tools/call",
            "params": {"name": name, "arguments": args, "_meta": {"claudecode/toolUseId": tool_id, "progressToken": 2}}}})
        result = ((reply or {}).get("mcp_response") or {}).get("result") or {}
        content = result.get("content") or [{"type": "text", "text": "(no output)"}]
        hook("PostToolUse", "mcp__opencode__" + name, args, tool_id, {"tool_response": content})
        emit(base({"type": "user", "parent_tool_use_id": None, "message": {"role": "user", "content": [
            {"tool_use_id": tool_id, "type": "tool_result", "content": content, "is_error": bool(result.get("isError"))}]}}))
        text = " ".join(part.get("text", "") for part in content if part.get("type") == "text").strip()
        first = [line for line in text.splitlines() if line.strip()][:3]
        seen = ("the tool said: " + "; ".join(first)) if first else "the tool returned nothing"
    answer = "Test replay reply. I ran " + (name or "no tool") + " and " + seen + "."
    msg_id = "msg_fake_" + uuid.uuid4().hex[:12]
    stream({"type": "message_start", "message": {"model": state["model"], "id": msg_id, "type": "message",
            "role": "assistant", "content": [], "stop_reason": None, "usage": usage()}}, msg_id)
    stream({"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}}, msg_id)
    for i in range(0, len(answer), 24):
        stream({"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": answer[i:i + 24]}}, msg_id)
    assistant(msg_id, [{"type": "text", "text": answer}])
    stream({"type": "content_block_stop", "index": 0}, msg_id)
    stream({"type": "message_delta", "delta": {"stop_reason": "end_turn", "stop_sequence": None}, "usage": usage()}, msg_id)
    stream({"type": "message_stop"}, msg_id)
    emit(base({"type": "result", "subtype": "success", "is_error": False, "duration_ms": 900,
               "duration_api_ms": 800, "num_turns": 2, "result": answer, "stop_reason": "end_turn",
               "total_cost_usd": 0, "usage": usage(), "modelUsage": {}, "permission_denials": [],
               "terminal_reason": "completed"}))


def answer(rid, response):
    emit({"type": "control_response", "response": {"subtype": "success", "request_id": rid, "response": response}})


for raw in sys.stdin:
    raw = raw.strip()
    if not raw:
        continue
    if log:
        log.write("< " + raw[:300] + "\n")
        log.flush()
    try:
        message = json.loads(raw)
    except ValueError:
        continue
    kind = message.get("type")
    if kind == "control_response":
        response = message.get("response") or {}
        slot = pending.get(response.get("request_id"))
        if slot:
            slot["response"] = response.get("response")
            slot["event"].set()
        continue
    if kind == "control_request":
        rid = message.get("request_id")
        body = message.get("request") or {}
        sub = body.get("subtype")
        if sub == "initialize":
            state["hooks"] = body.get("hooks") or {}
            manifest = (body.get("sdkMcpServerManifests") or {}).get("opencode") or {}
            state["tools"] = (manifest.get("toolsListResult") or {}).get("tools") or []
            if log:
                log.write("tools: " + ",".join(str(t.get("name")) for t in state["tools"]) + "\n")
                log.flush()
            reply = dict(STARTUP)
            if LOGGED_OUT:
                reply["account"] = {"tokenSource": "none", "apiProvider": "firstParty"}
            answer(rid, reply)
        elif sub == "get_context_usage":
            answer(rid, {"rawMaxTokens": 200000, "maxTokens": 200000, "totalTokens": 0, "categories": []})
        elif sub == "interrupt":
            answer(rid, {"still_queued": []})
        else:
            answer(rid, {})
        continue
    if kind == "user":
        threading.Thread(target=turn, args=(message,), daemon=True).start()
