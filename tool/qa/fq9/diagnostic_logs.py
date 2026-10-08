"""Bounded, category-only diagnostic logs; never persist raw or replaced text.

The caller obtains app-PID-filtered ``logcat -v epoch`` and the owned server log
tail in memory. PID authority must come from the verified app UID processes.
Only fixed native messages and structured OpenCode logger signatures survive.
Unknown lines are omitted, not evidence that no error occurred. Android
threadtime has neither year nor timezone: it cannot prove this epoch window.
OpenCode's ISO logger timestamps without a suffix use its UTC convention.
The frozen 1.18.32 Effect logger uses fully key=value fields, not the legacy
service-prefixed formatter. Actual prompt/processor call shapes below derive
from source 545f51d26cc39a907d2867492d498d9607ea5fa4. Frozen event publication
does not log a "publishing" signature; only the older formatter has that rule.
This module has no process, device, filesystem or network operations.
"""

from collections import Counter, deque
from datetime import datetime, timezone
import json
import re


MAX_BYTES = 512 * 1024
MAX_EVENTS = 200
MAX_LINE_BYTES = 16384
_MAX_TIMESTAMP_MS = 253402300799999
_EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)
_LEVELS = {
    "V": "verbose",
    "D": "debug",
    "I": "info",
    "W": "warning",
    "E": "error",
    "F": "fatal",
    "DEBUG": "debug",
    "INFO": "info",
    "WARN": "warning",
    "ERROR": "error",
}
_EFFECT_LEVELS = {
    "TRACE": "verbose",
    "DEBUG": "debug",
    "INFO": "info",
    "WARN": "warning",
    "ERROR": "error",
    "FATAL": "fatal",
    "Trace": "verbose",
    "Debug": "debug",
    "Info": "info",
    "Warn": "warning",
    "Error": "error",
    "Fatal": "fatal",
}
_TIMESTAMP = re.compile(
    r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
    r"(?:\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})?|[0-9]{10,12}(?:\.[0-9]{1,9})?"
)
_APP_EPOCH = re.compile(
    r"^\s*(?P<seconds>[0-9]{1,12})(?:\.(?P<fraction>[0-9]{1,9}))?\s+"
    r"(?P<pid>[0-9]{1,10})\s+(?P<tid>[0-9]{1,10})\s+(?P<level>[VDIWEF])\s+"
    r"(?P<tag>[A-Za-z][A-Za-z0-9_.-]{0,63})\s*:\s?(?P<message>.*)$"
)
_APP_THREADTIME = re.compile(
    r"^\s*[0-9]{2}-[0-9]{2}\s+[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3,9}\s+"
    r"(?P<pid>[0-9]{1,10})\s+[0-9]{1,10}\s+[VDIWEF]\s+[^:]{1,64}:"
)
_SERVER = re.compile(
    r"^(?P<level>DEBUG|INFO|WARN|ERROR)\s+"
    r"(?P<timestamp>[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
    r"(?:\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})?|[0-9]{10,12}(?:\.[0-9]{1,9})?)"
    r"\s+(?:\+[0-9]{1,12}ms\s+)?(?P<metadata>.*)$"
)
_FIELD = re.compile(r"([A-Za-z][A-Za-z0-9_.-]{0,63})=")
_ANSI_COLOR = re.compile(rb"\x1b\[[0-9;]{0,30}m")
_APP_MESSAGES = {
    ("OcLinux", "notification not updated"): "notification_update_failed",
    ("OcLinux", "running services not recorded"): "service_state_record_failed",
    (
        "OcLinux",
        "Service diagnostics could not be saved",
    ): "service_diagnostics_write_failed",
    ("OcLifecycle", "exit reasons unavailable"): "exit_history_unavailable",
}
_SERVER_MESSAGES = {
    ("session.prompt", "loop"): "session_prompt_loop",
    ("session.prompt", "error"): "session_prompt_error",
    ("session.processor", "start"): "session_processor_start",
}
CATEGORIES = frozenset(
    (
        *_APP_MESSAGES.values(),
        *_SERVER_MESSAGES.values(),
        "previous_process_exit",
        "session_error_published",
        "session_processor_error",
        "session_prompt_loop_exit",
        "session_prompt_cancel",
    )
)


def _epoch_ms(value):
    seconds, _, fraction = value.partition(".")
    result = int(seconds) * 1000 + int((fraction + "000")[:3])
    return result if 0 <= result <= _MAX_TIMESTAMP_MS else None


def _iso_ms(value):
    if value[0].isdigit() and "T" not in value:
        return _epoch_ms(value)
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        delta = parsed.astimezone(timezone.utc) - _EPOCH
        result = (
            delta.days * 86400 + delta.seconds
        ) * 1000 + delta.microseconds // 1000
        return result if 0 <= result <= _MAX_TIMESTAMP_MS else None
    except (ValueError, OverflowError):
        return None


def _metadata(value, *, effect=False):
    """Consume complete fields before the final fixed message, never substrings.

    OpenCode serializes string/object fields as JSON and plain scalars as bare
    tokens. Parse a JSON field in full so private values containing whitespace,
    fake service names or a final-message word cannot become structural fields.
    Only selected fields and the presence of annotation names are needed after
    tokenization. Effect's multiple string messages repeat message=; preserve
    its first authored message and consume the remaining private values.
    """
    position = 0
    seen = set()
    selected = {}
    decoder = json.JSONDecoder()
    while position < len(value):
        field = _FIELD.match(value, position)
        if field is None:
            return selected, value[position:], seen
        name = field[1]
        if name in seen and not (effect and name == "message"):
            return None
        seen.add(name)
        position = field.end()
        if position == len(value):
            return None
        if value[position] in '"[{':
            try:
                item, end = decoder.raw_decode(value, position)
            except (ValueError, RecursionError):
                return None
            position = end
            if position < len(value) and not value[position].isspace():
                return None
        else:
            end = position
            while end < len(value) and not value[end].isspace():
                end += 1
            item = value[position:end]
            position = end
        selected_names = (
            ("timestamp", "level", "message") if effect else ("service", "type")
        )
        if name in selected_names and name not in selected:
            # A JSON object/array in one of these fields is never an identifier.
            selected[name] = item if isinstance(item, str) else None
        while position < len(value) and value[position].isspace():
            position += 1
    return selected, "", seen


def _app_event(line, app_pids):
    header = _APP_EPOCH.fullmatch(line)
    if header is None:
        untimed = _APP_THREADTIME.match(line)
        return None, bool(untimed and int(untimed["pid"]) in app_pids)
    if int(header["pid"]) not in app_pids:
        return None, False
    message = header["message"]
    category = _APP_MESSAGES.get((header["tag"], message))
    if (
        category is None
        and header["tag"] == "OcLifecycle"
        and re.fullmatch(
            r"previous process ended: reason=[0-9]{1,9} status=-?[0-9]{1,9}", message
        )
    ):
        category = "previous_process_exit"
    if category is None:
        return None, False
    timestamp = _epoch_ms(header["seconds"] + "." + (header["fraction"] or "0"))
    if timestamp is None:
        return None, True
    return {
        "timestampMs": timestamp,
        "level": _LEVELS[header["level"]],
        "category": category,
    }, False


def _effect_event(line):
    fields = _metadata(line, effect=True)
    if fields is None:
        return None, False
    selected, tail, annotations = fields
    if tail or not {"timestamp", "level", "run", "message"}.issubset(annotations):
        return None, False
    raw_timestamp = selected.get("timestamp")
    if not isinstance(raw_timestamp, str) or not _TIMESTAMP.fullmatch(raw_timestamp):
        return None, True
    timestamp = _iso_ms(raw_timestamp)
    if timestamp is None:
        return None, True
    level = _EFFECT_LEVELS.get(selected.get("level"))
    message = selected.get("message")
    category = None
    if (
        level == "info"
        and {"session.id", "step"}.issubset(annotations)
        and message == "loop"
    ):
        category = "session_prompt_loop"
    elif {"session.id", "messageID"}.issubset(annotations) and message == "process":
        if level == "info":
            category = "session_processor_start"
        elif level == "error":
            category = "session_processor_error"
    elif level == "info" and "session.id" in annotations:
        if message == "exiting loop":
            category = "session_prompt_loop_exit"
        elif message == "cancel":
            category = "session_prompt_cancel"
    elif (
        level == "error"
        and "sessionID" in annotations
        and message == "Failed to drain Session"
    ):
        category = "session_prompt_error"
    if category is None:
        return None, False
    return {"timestampMs": timestamp, "level": level, "category": category}, False


def _server_event(line):
    if line.startswith("timestamp="):
        return _effect_event(line)
    header = _SERVER.fullmatch(line)
    if header is None:
        return None, bool(re.match(r"^(?:DEBUG|INFO|WARN|ERROR)\s", line))
    timestamp = _iso_ms(header["timestamp"])
    if timestamp is None:
        return None, True
    fields = _metadata(header["metadata"])
    if fields is None:
        return None, False
    selected, message, _ = fields
    service = selected.get("service")
    category = _SERVER_MESSAGES.get((service, message))
    if (
        service == "bus"
        and selected.get("type") == "session.error"
        and message == "publishing"
    ):
        category = "session_error_published"
    if category is None:
        return None, False
    return {
        "timestampMs": timestamp,
        "level": _LEVELS[header["level"]],
        "category": category,
    }, False


def project_log(raw, *, source, window_start_ms, window_end_ms, app_pids=None):
    """Return fixed categories in the inclusive window, or explicit unavailability.

    ``None`` or a non-bytes input means no capture; empty bytes are a captured
    empty log. The newest 512 KiB and newest 200 matched events are retained.
    No raw line, replaced line, field value, PID, account label or content hash
    is returned. ``omittedLines`` includes unknown, out-of-window and foreign-PID
    lines; a zero-event result makes no assertion about omitted error details.
    """
    if (
        source not in ("app", "server")
        or type(window_start_ms) is not int
        or type(window_end_ms) is not int
        or not 0 <= window_start_ms <= window_end_ms <= _MAX_TIMESTAMP_MS
        or (
            app_pids is not None
            and (
                not isinstance(app_pids, (set, frozenset))
                or any(
                    type(pid) is not int or not 0 < pid <= 2147483647
                    for pid in app_pids
                )
            )
        )
    ):
        raise ValueError("invalid_log_projection_contract")
    result = {
        "source": source,
        "captured": isinstance(raw, bytes),
        "windowStartMs": window_start_ms,
        "windowEndMs": window_end_ms,
        "inputBytes": len(raw) if isinstance(raw, bytes) else 0,
        "processedBytes": 0,
        "truncated": False,
        "eventsTruncated": False,
        "matchedLines": 0,
        "omittedLines": 0,
        "timeUnavailableLines": 0,
        "categoryCounts": {},
        "events": [],
    }
    if not result["captured"]:
        return result
    start = max(0, len(raw) - MAX_BYTES)
    chunk = raw[start:]
    result["truncated"] = start > 0
    if start and raw[start - 1 : start] != b"\n":
        # A cut field/message is not a complete authored signature.
        _, separator, chunk = chunk.partition(b"\n")
        if not separator:
            chunk = b""
    result["processedBytes"] = len(chunk)
    events = deque(maxlen=MAX_EVENTS)
    counts = Counter()
    pids = app_pids or frozenset()
    for raw_line in chunk.splitlines():
        if len(raw_line) > MAX_LINE_BYTES:
            result["omittedLines"] += 1
            continue
        line = _ANSI_COLOR.sub(b"", raw_line).decode("utf-8", errors="replace")
        event, untimed = (
            _app_event(line, pids) if source == "app" else _server_event(line)
        )
        if untimed:
            result["timeUnavailableLines"] += 1
        if (
            event is None
            or not window_start_ms <= event["timestampMs"] <= window_end_ms
        ):
            result["omittedLines"] += 1
            continue
        events.append(event)
        counts[event["category"]] += 1
        result["matchedLines"] += 1
    result["events"] = list(events)
    result["eventsTruncated"] = result["matchedLines"] > MAX_EVENTS
    result["categoryCounts"] = dict(counts)
    return result
