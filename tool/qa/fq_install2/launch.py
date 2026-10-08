"""Bounded stock-app launch rejection check; no ADB commands or device CLI.

The caller owns the emulator lock, APK/storage preflight, and screenshots.
The injected adapter supplies bounded ui(), text(node), tap_node(node), back(),
and optionally monotonic()/sleep(seconds). Start on Chats or Settings/Agents.

This checks New conversation -> agent picker -> target's existing fix action.
It never signs in, selects Claude, changes a project/draft, or sends a prompt.
All evidence copy is a closed set of app-authored strings; arbitrary screen
text and exceptions are never returned. Screenshots remain the caller's duty.

Actual daemon launch requires a chat-selectable (phone-checked, host-running,
signed-in) row and picker certification (install + smoke at the pinned version).
Controller startAgentChatIn creates a local gateway draft when firstPrompt is
empty. Only a nonempty prompt reaches PaseoGateway._createAgent and the daemon's
create_agent_request (90s timeout). Neither a draft nor this picker rejection
is daemon-launch evidence; daemonLaunchObserved is always false here.
"""

import math
import time


TARGETS = {
    "codex": "Codex",
    "gemini": "Gemini CLI",
    "qwen": "Qwen Code",
    "goose": "Goose",
    "omp-acp": "Oh My Pi",
    "fx": "fx",
}
_BLOCKERS = frozenset({
    "Sign in needed", "Not certified on this version yet",
    "Sign-in isn't ready on this phone yet", "Checking sign-in…",
    "Phone check needed", "Not available on this phone yet",
})


def run_launch(device, agentId, name, deadlineSeconds=30):
    """Return closed rejection facts; never bypass auth/certification gates.

    Invalid targets/names raise ValueError before reading or operating the UI.
    Adapter failures and deadline expiry fail closed as entrypoint_missing.
    Frames opened by this driver are unwound via Back before returning. Each
    adapter call must itself have a bounded timeout; this deadline cannot
    preempt a blocked adapter call.
    """
    if agentId not in TARGETS or TARGETS[agentId] != name:
        raise ValueError("Unsupported install-cert target")
    if (isinstance(deadlineSeconds, bool) or
            not isinstance(deadlineSeconds, (int, float)) or
            not math.isfinite(deadlineSeconds) or not 0 < deadlineSeconds <= 120):
        raise ValueError("Invalid launch-check deadline")
    now = getattr(device, "monotonic", time.monotonic)
    pause = getattr(device, "sleep", time.sleep)
    began = now()
    deadline = began + deadlineSeconds
    opened = 0
    picker_observed = False
    plain = []
    state, code = "entrypoint_missing", "navigation_missing"
    unwound = True
    captured = False

    def read():
        if now() >= deadline:
            raise TimeoutError
        nodes = device.ui()
        if now() >= deadline:
            raise TimeoutError
        return [(node, device.text(node).replace("\u2068", "").replace("\u2069", ""))
                for node in nodes]

    def wait():
        remaining = deadline - now()
        if remaining <= 0:
            raise TimeoutError
        pause(min(.25, remaining))

    def exact(rows, labels):
        return next((node for node, text in rows if text in labels), None)

    def tap(rows, labels):
        nonlocal opened
        node = exact(rows, labels)
        if node is None:
            return False
        # All allowed actions below are navigation or the target's inspection
        # action. In particular Sign in with/Use/Claude labels are absent.
        if now() >= deadline:
            raise TimeoutError
        device.tap_node(node)
        opened += 1
        return True

    try:
        # Agents and Settings are allowed entrypoints, but no arbitrary Back
        # loop: at most two known frames are popped to reach Chats.
        backs = 0
        while True:
            rows = read()
            labels = {text for _, text in rows}
            if "What should we work on?" in labels:
                break
            if tap(rows, {"New conversation"}):
                break
            if backs < 2 and ("Check this phone" in labels or "Settings" in labels):
                device.back()
                backs += 1
            else:
                wait()
        while True:
            rows = read()
            if "What should we work on?" in {text for _, text in rows}:
                if tap(rows, {"Agent OpenCode. Change agent", "OpenCode",
                              "Agent Claude Code. Change agent"}):
                    break
            wait()
        while True:
            rows = read()
            if "Choose an agent" not in {text for _, text in rows}:
                wait()
                continue
            target_rows = [text for _, text in rows if name in text.splitlines()]
            if not target_rows:
                state, code = "entrypoint_missing", "target_missing"
                break
            picker_observed = True
            target_copy = {line for text in target_rows for line in text.splitlines()}
            plain = sorted(target_copy & _BLOCKERS)
            if "Not certified on this version yet" in target_copy:
                state, code = "blocked_by_certification", "chat_not_certified"
                break
            if "Sign in needed" not in target_copy:
                state, code = "entrypoint_missing", "target_not_auth_blocked"
                break
            # Exact semantics label scopes the tap to this target. Never use
            # generic Sign in or a merged row's center (may hit another agent).
            if not tap(rows, {"Sign in to " + name}):
                state, code = "blocked_by_auth", "target_fix_action_missing"
                break
            while True:
                rows = read()
                labels = {text for _, text in rows}
                title = "Sign in with " + name
                # Current sheet title is Sign in with <agent>; the primary
                # sign-in-start control has the same copy. We only observe it.
                intro = (name + " signs in with its own prompts, on your own account "
                         "with its provider. The app never sees your sign-in.")
                if title in labels and (intro in labels or
                        "Sign-in isn't ready on this phone yet" in labels):
                    plain = sorted(set(plain) | (labels & _BLOCKERS))
                    state, code = "app_rejection_with_way_forward", "auth_required"
                    break
                wait()
            break
    except TimeoutError:
        state, code = "entrypoint_missing", "deadline_exceeded"
    except Exception:
        state, code = "entrypoint_missing", "adapter_failed"
    finally:
        capture = getattr(device, 'capture_launch_rejection', None)
        if capture is not None and plain and state in {
                'app_rejection_with_way_forward', 'blocked_by_auth', 'blocked_by_certification'}:
            try:
                capture()  # Ignore its return; never export raw copy or paths.
                captured = True
            except Exception:
                state, code = 'entrypoint_missing', 'rejection_capture_failed'
        # We never edit/send the draft or choose a project. Back only unwinds
        # frames this driver opened. A cleanup failure invalidates the result.
        for _ in range(opened):
            try:
                device.back()
            except Exception:
                unwound = False
                state, code = "entrypoint_missing", "navigation_restore_failed"
                break
    return {
        "agentId": agentId,
        "state": state,
        "code": code,
        "appPathObserved": picker_observed,
        "plainError": bool(plain),
        "noHang": code not in {"deadline_exceeded", "adapter_failed", "navigation_restore_failed", "rejection_capture_failed"},
        "daemonLaunchObserved": False,
        "navigationUnwound": unwound,
        "rejectionCaptured": captured,
        "plainCopy": plain,
        "elapsedSeconds": round(max(0, now() - began), 3),
    }
