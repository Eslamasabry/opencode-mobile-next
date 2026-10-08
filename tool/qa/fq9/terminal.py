"""Bounded OC1 terminal evidence; never exports text, outputs or identities.

This projection does not query, abort or delete a session. Callers fetch the
owned history/status before cleanup and persist only this result.
"""

import re

from .common import DriverFailure

FAIL_CODES = frozenset(
    {
        "terminal_receipt_invalid",
        "terminal_history_invalid",
        "terminal_status_invalid",
        "terminal_scope_mismatch",
        "terminal_prompt_mismatch",
    }
)
ERROR_NAMES = frozenset(
    {
        "ProviderAuthError",
        "UnknownError",
        "MessageOutputLengthError",
        "MessageAbortedError",
        "StructuredOutputError",
        "ContextOverflowError",
        "ContentFilterError",
        "APIError",
    }
)
FINISH_REASONS = frozenset({"stop", "tool-calls", "length", "content-filter", "error"})
TOOL_STATES = ("pending", "running", "completed", "error", "unknown")
MAX_HISTORY = 199
MAX_PARTS_PER_MESSAGE = 512
MAX_PARTS = 4096
MAX_INTEGER = 2**53 - 1


def _integer(value, code):
    if value is None:
        return None
    if type(value) is not int or not 0 <= value <= MAX_INTEGER:
        raise DriverFailure(code)
    return value


def _times(raw):
    if type(raw) is not dict:
        raise DriverFailure("terminal_history_invalid")
    created = _integer(raw.get("created"), "terminal_history_invalid")
    if created is None:
        raise DriverFailure("terminal_history_invalid")
    return {
        "created": created,
        "completed": _integer(raw.get("completed"), "terminal_history_invalid"),
    }


def _identifier(value):
    return type(value) is str and re.fullmatch(r"[A-Za-z0-9_-]{1,100}", value)


def _receipt(raw):
    if type(raw) is not dict or raw.get("engine") != "opencode":
        raise DriverFailure("terminal_receipt_invalid")
    sessions = raw.get("sessions")
    directory = raw.get("directory")
    if (
        type(sessions) is not list
        or len(sessions) != 1
        or type(sessions[0]) is not dict
        or type(directory) is not str
        or not re.fullmatch(r"/root/projects/[A-Za-z0-9_-]{1,100}", directory)
        or any(not _identifier(sessions[0].get(key)) for key in ("id", "promptID"))
        or type(sessions[0].get("title")) is not str
        or not re.fullmatch(r"[A-Za-z0-9 _.-]{1,160}", sessions[0]["title"])
    ):
        raise DriverFailure("terminal_receipt_invalid")
    return sessions[0]


def _status(raw, session_id):
    if type(raw) is not dict or len(raw) > 512:
        raise DriverFailure("terminal_status_invalid")
    status = raw.get(session_id, {"type": "idle"})
    if type(status) is not dict:
        raise DriverFailure("terminal_status_invalid")
    kind = status.get("type")
    kind = (
        kind if type(kind) is str and kind in ("busy", "retry", "idle") else "unknown"
    )
    retry = None
    if kind == "retry":
        retry = {
            key: _integer(status.get(key), "terminal_status_invalid")
            for key in ("attempt", "next")
        }
    return kind, retry


def project_terminal(history, status, receipt):
    """Project the latest owned turn from OC1 message array and status map.

    History must be complete within the fixed 199-message bound, in the OC1
    chronological order. Missing status-map entry is OC1 idle; completed
    tool-calls are intermediate assistant steps, never final completion.
    """
    session = _receipt(receipt)
    session_state, retry = _status(status, session["id"])
    if type(history) is not list or not 1 <= len(history) <= MAX_HISTORY:
        raise DriverFailure("terminal_history_invalid")
    users, replies, seen_ids = [], [], set()
    part_count = 0
    for message in history:
        if type(message) is not dict or type(message.get("info")) is not dict:
            raise DriverFailure("terminal_history_invalid")
        info = message["info"]
        if info.get("sessionID") != session["id"]:
            raise DriverFailure("terminal_scope_mismatch")
        if not _identifier(info.get("id")) or info["id"] in seen_ids:
            raise DriverFailure("terminal_history_invalid")
        seen_ids.add(info["id"])
        _times(info.get("time"))
        parts = message.get("parts")
        if type(parts) is not list or len(parts) > MAX_PARTS_PER_MESSAGE:
            raise DriverFailure("terminal_history_invalid")
        part_count += len(parts)
        if part_count > MAX_PARTS:
            raise DriverFailure("terminal_history_invalid")
        for part in parts:
            if type(part) is not dict:
                raise DriverFailure("terminal_history_invalid")
            if ("sessionID" in part and part["sessionID"] != session["id"]) or (
                "messageID" in part and part["messageID"] != info["id"]
            ):
                raise DriverFailure("terminal_scope_mismatch")
        role = info.get("role")
        if role == "user":
            users.append(info)
        elif role == "assistant":
            if not _identifier(info.get("parentID")):
                raise DriverFailure("terminal_scope_mismatch")
            if info["parentID"] == session["promptID"]:
                replies.append(message)
        else:
            raise DriverFailure("terminal_history_invalid")
    if not users or users[-1]["id"] != session["promptID"]:
        raise DriverFailure("terminal_prompt_mismatch")

    tools = {"counts": dict.fromkeys(TOOL_STATES, 0), "timings": []}
    for message in replies:
        for part in message["parts"]:
            if part.get("type") != "tool":
                continue
            if (
                part.get("sessionID") != session["id"]
                or part.get("messageID") != message["info"]["id"]
                or part.get("synthetic") is True
            ):
                raise DriverFailure("terminal_scope_mismatch")
            state = part.get("state")
            if type(state) is not dict:
                raise DriverFailure("terminal_history_invalid")
            kind = state.get("status")
            kind = kind if type(kind) is str and kind in TOOL_STATES else "unknown"
            tools["counts"][kind] += 1
            time = state.get("time", {})
            inputs = state.get("input", {})
            if type(time) is not dict or type(inputs) is not dict:
                raise DriverFailure("terminal_history_invalid")
            tools["timings"].append(
                {
                    "state": kind,
                    "start": _integer(time.get("start"), "terminal_history_invalid"),
                    "end": _integer(time.get("end"), "terminal_history_invalid"),
                    "timeoutMs": _integer(
                        inputs.get("timeout"), "terminal_history_invalid"
                    ),
                }
            )

    latest = None
    has_error = False
    if replies:
        info = replies[-1]["info"]
        finish = info.get("finish")
        finish = (
            finish if type(finish) is str and finish in FINISH_REASONS else "unknown"
        )
        error = info.get("error")
        has_error = error is not None
        category = None
        if has_error:
            if type(error) is not dict:
                raise DriverFailure("terminal_history_invalid")
            name = error.get("name")
            category = name if type(name) is str and name in ERROR_NAMES else "unknown"
        latest = {
            **_times(info["time"]),
            "finishReason": finish,
            "errorCategory": category,
        }

    if latest and latest["errorCategory"] == "MessageAbortedError":
        outcome = "aborted"
    elif has_error or (latest and latest["finishReason"] == "error"):
        outcome = "errored"
    elif session_state == "retry":
        outcome = "retrying"
    elif session_state == "busy":
        outcome = "running"
    elif session_state == "idle":
        if (
            latest
            and latest["completed"] is not None
            and latest["finishReason"] in ("stop", "length", "content-filter")
        ):
            outcome = "completed"
        elif tools["counts"]["error"]:
            outcome = "errored"
        else:
            outcome = "inactive"
    else:
        outcome = "unknown"
    return {
        "schema": 1,
        "ownedSession": True,
        "sessionState": session_state,
        "outcome": outcome,
        "latestUser": _times(users[-1]["time"]),
        "latestAssistant": latest,
        "retry": retry,
        "tools": tools,
    }
