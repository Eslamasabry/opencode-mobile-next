"""Severity-only counts for unknown diagnostic messages; no text is exported."""

from collections import Counter
from datetime import datetime
import re

LEVELS = {
    "V": "verbose",
    "D": "debug",
    "I": "info",
    "W": "warning",
    "E": "error",
    "F": "fatal",
    "TRACE": "verbose",
    "DEBUG": "debug",
    "INFO": "info",
    "WARN": "warning",
    "ERROR": "error",
    "FATAL": "fatal",
}
EFFECT = re.compile(rb"^timestamp=([0-9T:.Z+-]{20,40}) level=([A-Z]{4,5})\b")
APP = re.compile(
    rb"^\s*([0-9]{10,12}(?:\.[0-9]{1,9})?)\s+[0-9]+\s+[0-9]+\s+([VDIWEF])\s"
)


def project_severity(raw, *, source, start_ms, end_ms):
    if (
        type(raw) is not bytes
        or len(raw) > 512 * 1024
        or source not in ("app", "server")
        or type(start_ms) is not int
        or type(end_ms) is not int
        or not 0 <= start_ms <= end_ms
    ):
        raise ValueError("severity_input_invalid")
    counts = Counter()
    matched = omitted = 0
    first = last = None
    for line in raw.splitlines():
        m = (APP if source == "app" else EFFECT).match(line)
        if not m:
            omitted += 1
            continue
        try:
            stamp = m[1].decode("ascii")
            if source == "app":
                seconds, _, frac = stamp.partition(".")
                time = int(seconds) * 1000 + int((frac + "000")[:3])
            else:
                value = datetime.fromisoformat(stamp.replace("Z", "+00:00"))
                if value.tzinfo is None:
                    raise ValueError()
                time = int(value.timestamp() * 1000)
            level = LEVELS[m[2].decode("ascii")]
        except (ValueError, KeyError, UnicodeError):
            omitted += 1
            continue
        if not start_ms <= time <= end_ms:
            omitted += 1
            continue
        counts[level] += 1
        matched += 1
        first = time if first is None else min(first, time)
        last = time if last is None else max(last, time)
    return dict(
        source=source,
        windowStartMs=start_ms,
        windowEndMs=end_ms,
        matchedLines=matched,
        omittedLines=omitted,
        levelCounts=dict(counts),
        firstTimestampMs=first,
        lastTimestampMs=last,
    )
