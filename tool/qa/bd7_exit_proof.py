"""Fail-closed projections of Android exit history and private crash records.

Dump grammar: AOSP ApplicationExitInfo.dump and AppExitInfoTracker.dumpHistory:
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ApplicationExitInfo.java
https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/AppExitInfoTracker.java
Descriptions, process names, paths and traces never enter the returned evidence.
"""
import json
import re
import time


class ProofFailure(ValueError):
    """A fixed failure code; no input or decoder exception is exposed."""


_PACKAGE = re.compile(r'[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+')
_PACKAGE_HEADER = re.compile(r'([ \t]*)package: ([A-Za-z0-9_.]+)[ \t]*')
_HISTORY_HEADER = re.compile(r'([ \t]*)Historical Process Exit for uid=(-?[0-9]+)[ \t]*')
_RECORD_HEADER = re.compile(r'([ \t]*)ApplicationExitInfo #[0-9]+:[ \t]*')
_IDENTITY = re.compile(
    r'[ \t]*timestamp=[0-9]{4}-[0-9]{2}-[0-9]{2} '
    r'[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3} '
    r'pid=([0-9]+) realUid=(-?[0-9]+) packageUid=(-?[0-9]+) '
    r'definingUid=(-?[0-9]+) user=(-?[0-9]+)[ \t]*'
)
# The textual reason may itself contain parentheses: APP CRASH(EXCEPTION).
_OUTCOME = re.compile(
    r'[ \t]*process=([^\s]+) reason=([0-9]+) \([^\r\n]+\) '
    r'subreason=([0-9]+) \([^\r\n]+\) status=(-?[0-9]+)[ \t]*'
)
_SOURCES = frozenset({'flutter', 'platform', 'widget', 'native', 'anr'})
_CATEGORIES = frozenset({
    'Invalid state', 'Invalid argument', 'Missing value',
    'Unsupported operation', 'Application error', 'Native application error',
    'Permission denied', 'Input/output failure', 'Interrupted operation',
    'Android reported that the app stopped responding',
})


def _decode(raw: bytes, limit: int) -> str:
    if type(raw) is not bytes or len(raw) > limit:
        raise ValueError()
    return raw.decode('utf-8', errors='strict')


def _int32(value: str) -> int:
    result = int(value)
    if not -(1 << 31) <= result < (1 << 31):
        raise ValueError()
    return result


def parse_exit_history(raw: bytes, package: str, expected_pid: int, reason: int) -> dict:
    """Require one exact-main record for the requested JVM-crash/ANR PID."""
    try:
        if (type(package) is not str or _PACKAGE.fullmatch(package) is None
                or type(expected_pid) is not int or not 0 < expected_pid < (1 << 31)
                or type(reason) is not int or reason not in (4, 6)):
            raise ValueError()
        lines = _decode(raw, 2 * 1024 * 1024).splitlines()
        active_package = None
        package_indent = -1
        history_indent = None
        seen_pids = set()
        found = None
        index = 0
        while index < len(lines):
            line = lines[index]
            package_header = _PACKAGE_HEADER.fullmatch(line)
            if package_header is not None:
                active_package = package_header[2]
                package_indent = len(package_header[1])
                history_indent = None
                index += 1
                continue
            if line.lstrip().startswith('package:'):
                raise ValueError()
            if active_package != package:
                index += 1
                continue
            history_header = _HISTORY_HEADER.fullmatch(line)
            if history_header is not None:
                if len(history_header[1]) <= package_indent:
                    raise ValueError()
                _int32(history_header[2])
                history_indent = len(history_header[1])
                index += 1
                continue
            header = _RECORD_HEADER.fullmatch(line)
            if header is not None:
                if (history_indent is None or len(header[1]) <= history_indent
                        or index + 2 >= len(lines)):
                    raise ValueError()
                identity = _IDENTITY.fullmatch(lines[index + 1])
                outcome = _OUTCOME.fullmatch(lines[index + 2])
                if identity is None or outcome is None:
                    raise ValueError()
                for metadata in lines[index + 1:index + 3]:
                    indent = len(metadata) - len(metadata.lstrip(' \t'))
                    if indent <= len(header[1]):
                        raise ValueError()
                numbers = [_int32(value) for value in identity.groups()]
                pid = numbers[0]
                parsed_reason = _int32(outcome[2])
                _int32(outcome[3])
                status = _int32(outcome[4])
                if pid <= 0 or pid in seen_pids:
                    raise ValueError()
                seen_pids.add(pid)
                if pid == expected_pid:
                    if outcome[1] != package or parsed_reason != reason:
                        raise ValueError()
                    found = {'pid': pid, 'reason': parsed_reason,
                             'status': status, 'exact_main': True}
                index += 3
                continue
            # Reject malformed headers rather than treating them as absent rows.
            if line.lstrip().startswith('ApplicationExitInfo'):
                raise ValueError()
            index += 1
        if found is None:
            raise ValueError()
        return found
    except (ValueError, TypeError, OverflowError):
        raise ProofFailure('exit_proof_invalid') from None


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError()
        result[key] = value
    return result


def _reject_constant(_value):
    raise ValueError()


def parse_crash_ring(raw: bytes, expected_source: str, after_millis: int) -> dict:
    """Validate every stored row, then project the newest post-trigger record."""
    try:
        if (type(expected_source) is not str or expected_source not in _SOURCES
                or type(after_millis) is not int or after_millis < 0):
            raise ValueError()
        records = json.loads(_decode(raw, 8192), object_pairs_hook=_unique_object,
                             parse_constant=_reject_constant)
        if type(records) is not list or not 0 < len(records) <= 20:
            raise ValueError()
        now_millis = time.time_ns() // 1_000_000
        found = None
        for record in records:
            if type(record) is not dict or set(record) != {'source', 'category', 'time'}:
                raise ValueError()
            source, category, millis = record['source'], record['category'], record['time']
            if (type(source) is not str or source not in _SOURCES
                    or type(category) is not str or category not in _CATEGORIES
                    or type(millis) is not int or not 0 < millis <= now_millis):
                raise ValueError()
            if (source == expected_source and millis > after_millis
                    and (found is None or millis > found['time'])):
                found = {'source': source, 'category': category, 'time': millis}
        if found is None:
            raise ValueError()
        return found
    except (ValueError, TypeError, OverflowError, RecursionError):
        raise ProofFailure('crash_ring_invalid') from None
