"""One real foreground tool with a 45-minute minimum successful duration."""

from .common import DriverFailure

COMMAND = 'i=0; while [ "$i" -lt 90 ]; do sleep 30; i=$((i+1)); echo FQ9_TICK:$i; done'
TIMEOUT_MS = 3000000
PROMPT = (
    "FQ9_BACKGROUND_FIXTURE_V2. Execute exactly one foreground bash tool call. "
    "Set timeout to the JSON NUMBER 3000000 (not a quoted string) milliseconds and command to exactly: "
    + COMMAND
    + ". "
    "Do not change the command, run it in background, use parallel calls, write "
    "files or run other tools. Wait for this call to finish before replying DONE. "
    "The ninety sequential sleeps intentionally take at least 45 minutes."
)


def tick_progress(state):
    """Only strict output from the exact executed tool counts as progress."""
    inputs = state.get("input", {})
    if inputs.get("command") != COMMAND or inputs.get("timeout") != TIMEOUT_MS:
        raise DriverFailure("live_fixture_command_invalid")
    if state.get("status") not in ("running", "completed"):
        return 0
    raw = state.get("metadata", {}).get("output")
    if raw is None and state.get("status") == "completed":
        raw = state.get("output", "")
    if raw is None:
        raw = ""
    if not isinstance(raw, str) or len(raw) > 4096:
        raise DriverFailure("live_fixture_command_invalid")
    # A stream update may end midway through the next line. Count complete
    # exact lines only, with no arbitrary stdout text admitted as a heartbeat.
    lines = raw.split("\n")
    partial = lines.pop()
    for index, line in enumerate(lines, 1):
        if line != f"FQ9_TICK:{index}" or index > 90:
            raise DriverFailure("live_fixture_command_invalid")
    if partial and not f"FQ9_TICK:{len(lines) + 1}".startswith(partial):
        raise DriverFailure("live_fixture_command_invalid")
    return len(lines)
