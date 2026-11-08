# Health and diagnostics telemetry (2.1.5 and native 3.0)

Owner decision, 2026-11-08: collect what we need to debug properly. This file is the one contract for Flutter 2.1.5 and native 3.0. Both apps send the same event names, parameters and values.

## Rules

- **Values come from fixed lists or numeric ranges only.** The adapter drops anything else.
- **Never sent:** prompts, replies, file names or paths, URLs, host names, IP addresses, error message text, keys or tokens, the advertising ID, or the Android ID.
- **Errors are reported as a kind,** never as their message text.
- **Consent.** There are three choices, and the first two start off:
  1. **Usage and app health** (`usage`): the events in sections B, C and D.
  2. **Crash reports** (`crashes`): section A.
  3. **Problem report** (`report`): section E. It's asked every time one is sent.
- **The first-start sheet** ("Help improve OpenCode Mobile?") offers:
  - "Share both", which turns on `usage` and `crashes`;
  - "Not now";
  - a "Choose" link that opens the two switches.

  Settings → Privacy has the same two switches. An answer already given in 2.1.5 carries over: the old single `telemetry` yes maps to `usage` on, and the old crash-offer answer maps to `crashes`.
- **Turning a switch off** deletes unsent data and resets the analytics data, as today.

## A. Crashes (Crashlytics, `crashes` consent)

| What | Detail |
|---|---|
| Fatal and non-fatal errors | Exception class and stack frames. The message is dropped, as in F1 of the security audit. |
| ANRs and process ends | On the next start, read `ApplicationExitInfo` (API 30+) for the last process end. Record `exit_reason`, one of: crash, crash_native, anr, low_memory, excessive_resource, signaled, user_requested, dependency_died, other. Record it as a non-fatal with that key. |
| Breadcrumbs | The last 30 steps, as `area.action` words from the list in section F, attached as Crashlytics logs. |
| Custom keys | `app_version`, `build_kind` (store, sideload, ci, local), `server_kind` (phone, computer, none), `engine_state` (stopped, starting, ready, failing), `connections` (0, 1, 2-3, 4+), `oc_server_version` (major.minor only). |

## B. Flows (Analytics events, `usage` consent)

| Event | Parameters |
|---|---|
| `conn_attempt` | `server_kind`, `result` (ok, fail), `fail_kind` (dns, tls, auth, timeout, refused, server_5xx, protocol, offline, other), `ms_range` (<500, 500-2000, 2000-10000, >10000) |
| `conn_drop` | `server_kind`, `reason` (network_change, server_closed, idle_timeout, background, other), `recovered` (yes, no) |
| `send_result` | `result` (ok, fail, queued), `fail_kind` (as `conn_attempt`, plus rejected and too_large), `first_reply_range` (<2s, 2-10s, 10-30s, >30s, none) |
| `turn_stall` | `stage` (waiting_tool, waiting_approval, streaming, other), `minutes_range` |
| `queue_flush` | `sent` (a count range), `failed` (a count range) |
| `linux_setup_step` | `step` (download, unpack, essentials, node, opencode, paseo, agents, done), `result`, `ms_range`, `exit_kind` (0, 1, 126, 127, signal, other), `signal` (SIGILL, SIGSEGV, SIGBUS, SIGKILL, other, none), `compat_mode` (none, armcap, threads, both), `free_space_range` |
| `agent_start` | `agent` (claude_code, codex, gemini, antigravity, opencode, other), `result`, `fail_kind` (not_installed, needs_sign_in, crashed, timeout, other) |
| `bg_service_end` | `reason` (os_limit, user_stopped, idle, crashed, other), `battery_exempt` (yes, no) |
| `update_result` | `kind` (in_app, patch), `result` (installed, failed, declined), `fail_kind` (download, checksum, signer, version, installer, other) |

The existing events stay: `app_open`, `setup_step_*`, `first_reply`, `screen_view`, `feature_used`, `session_length`, `update_offered`, `update_installed`.

## C. Performance (`usage` consent)

| Event | Parameters |
|---|---|
| `app_start` | `kind` (cold, warm), `ms_range` (<500, 500-1000, 1000-2000, 2000-5000, >5000) |
| `jank` | Sent once per session per page kind: `screen` (the list from `screen_view`), `janky_pct_range` (<1, 1-5, 5-15, >15), `refresh_hz` (60, 90, 120, other) |
| `memory_pressure` | `level` (moderate, low, critical) |

## D. Device context (`usage` consent, set once as user properties)

| Property | Values |
|---|---|
| `ram_range` | <3, 3-4, 4-6, 6-8, 8-12, >12 (GB) |
| `free_storage_range` | <2, 2-8, 8-32, >32 (GB) |
| `abi` | arm64, arm32, x86_64 |
| `webview_major` | A number |
| `notif_allowed` | yes, no |
| `server_kind` | phone, computer, both |
| `oc_server_version` | major.minor |

Model, Android version, app version, language and country are added by Google Analytics itself.

## E. Problem report (asked each time)

- **Where it starts:** Help → "Send a problem report".
- **What it collects:** the last 500 log lines from the app's own ring buffer, the custom keys from section A, the exit reasons, and the device context.
- **Redaction:** before anything is shown, run the existing redactor (`KitRedact` and redaction.dart) over the text. It blanks keys, tokens, URLs, host names, IPs and paths.
- **Preview:** the person sees the full text and can then "Send" or "Cancel".
- **Sending:** as one Crashlytics non-fatal with the text attached as logs, so we use no new backend.
- **Report code:** shown afterwards and copyable. It is the first 8 characters of the Firebase app-instance ID plus a counter. Settings → About also shows "Report code" with Copy, so a person can quote it in a GitHub issue.

## F. Breadcrumb words

The format is `area.action`:

| Area | Actions |
|---|---|
| `app` | start, foreground, background |
| `nav` | open:<screen word> |
| `chat` | send, stop, approve, deny, tool_wait |
| `conn` | connect, drop, reconnect |
| `linux` | step:<step word>, fail |
| `agent` | start:<agent word>, fail |
| `update` | offer, install, fail |
| `settings` | change:<setting group word> |
