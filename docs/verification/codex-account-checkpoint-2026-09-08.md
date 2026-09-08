# Codex account panel — local verification, 2026-09-08

## Delivered journey

Connected Codex profile → Servers menu → Codex account → explicit official
ChatGPT device-code sign-in → browser completion or owned cancellation → fresh
host account, reported rate limits and token usage. Reconnect reads fresh state
without replaying login. Connection/profile/location changes retire the panel.
This feature adds no persistent account data, provider tokens, or device codes;
existing profile deletion therefore has no new account storage to sweep.

The panel is enabled only for the locally pinned `0.153.4` initialize identity.
Unsupported versions/methods remain unavailable. API-key and other host auth are
not described as ChatGPT subscription usage. Null/missing metrics are unknown;
reported zero remains zero. Exact windows are displayed in days/hours/minutes,
with the host's reset time where supplied. Token totals are not bills or a
remaining message allowance.

No logout, account-switch UI, external-token auth, billing/provider actions,
credential-file access, or live account sign-in was part of development.

## Pinned callable proof and its limits

The installed official binary
`C:/Users/Eslam/AppData/Local/Programs/OpenAI/Codex/bin/codex.exe` reported exactly
`codex-cli 0.153.4`. Its earlier successful
`app-server generate-json-schema --out build/traycer/codex-schema` output includes
stable `account/read`, `account/login/start(chatgptDeviceCode)`, its returned
login ID/code/URL, `account/login/cancel` (`canceled`/`notFound`), matching login
completion/account update notifications, rate-limit reads and usage reads.
The [official app-server documentation](https://learn.chatgpt.com/docs/app-server)
corroborates these methods; current documentation does not replace pinned proof.

The bounded `tool/qa/codex_account_proof.py` executed successfully using stdio,
an isolated empty child home, file-only credential configuration, disabled
analytics/feedback, no inherited account/provider environment variables, and
outbound proxy variables pointed at an unused loopback port. This is process
configuration isolation, not a verified operating-system firewall.

| Actual official CLI call | Observed result |
| --- | --- |
| `--version` | Exactly `0.153.4` |
| `initialize` / `initialized` | Pinned identity, experimental API disabled |
| `account/read` with `refreshToken:false` | Explicit null account, `requiresOpenaiAuth:true` |
| `account/rateLimits/read` | Recognized method, explicit unauthenticated rejection `-32600` |
| `account/usage/read` | Recognized method, explicit unauthenticated rejection `-32600` |
| Exact spawned process-tree cleanup | Closed successfully |

Sanitized results: [official CLI result](codex-account-real-cli-2026-09-08.json).
No listener was opened. No live login/start/cancel/logout/provider request or
credential-file read was performed. Device start/completion/cancellation and
successful metric payloads are **pinned-schema plus synthetic fixture evidence**,
not a real account sign-in or subscription-data verification. Stdio method
callability is proved; the new account journey over the app's authenticated
WebSocket uses synthetic transport fixtures. Existing authenticated WebSocket
proof is recorded separately in
[codex-connection-2026-09-07.md](codex-connection-2026-09-07.md).

## Ownership and privacy behavior

- Each panel session is pinned to gateway/location and transport epoch. Mutations
  never reconnect or cross a replacement scope. Start performs a fresh signed-out
  preflight. Account operations are host-global, which the panel states explicitly.
- Only the returned login ID owns completion/cancel. Foreign and duplicate
  completion notifications cannot finish the flow. Early matching completion
  cannot restore a code after the start response arrives late.
- Cancel/close during start cancels a late owned receipt only on the same scope.
  A missing start receipt, timeout, or unconfirmed cancellation remains uncertain;
  it never becomes successful cancellation or an automatic resend. `notFound`
  retains the known ID for explicit cancellation retry, without logging out.
- Account notifications clear old identity/metrics immediately; revision checks
  reject stale read results. Browser background/return keeps the explicitly started
  device attempt alive and refreshes on resume. Scope loss clears the code.
- The verification URL must match the official HTTPS device route and still uses
  `openExternalLink` review. The code stays in transient selectable UI; it is not
  sent in the URL, stored, logged, or included in diagnostic/error/notification copy.

## Focused validation

Own Flutter 3.47.2 / Dart 3.13.2 environment, no copied `.dart_tool`.
`flutter pub get` resolved dependencies but exited 1 on the known Windows plugin
symlink/Developer Mode requirement. No machine settings changed. The generated
own package configuration supports subsequent `--no-pub` checks.

Commands, run as focused serial groups:

```text
<cached-python> tool/qa/codex_account_proof.py
flutter pub get
flutter gen-l10n
dart format <changed Dart files>
flutter test --no-pub --concurrency=1 test/codex_account_session_test.dart test/agent_account_controller_test.dart test/agent_account_widget_test.dart
flutter test --no-pub --concurrency=1 test/codex_transport_test.dart test/codex_gateway_test.dart test/server_codex_connect_flow_test.dart test/codex_connection_probe_test.dart test/codex_navigation_test.dart
flutter analyze --no-pub
```

**86 distinct focused tests passed:** 17 new adapter, 10 controller, 19 production
widget/route/lifecycle/capture cases, and 40 existing transport/gateway/Servers/
connection/navigation checks. This is not the full repository suite or native
Android verification. No APK, signing, CI, push or release ran in this worktree.

The first widget compile found an optional-interface promotion error, fixed with
an explicit checked `AgentAccountGateway` cast. The initial analyzer found only
braces/import diagnostics in the new files; these were corrected without ignores.
Capture inspection then corrected pending/uncertain headlines and formatted exact
window durations. The complete affected widget file was rerun after those changes.
Final analyzer is clean.

## Rendering evidence

`CODEX_ACCOUNT_CAPTURE_DIR=build/traycer/account-captures` enables 20 PNGs from the
actual production panel using synthetic account/code/usage fixtures and bundled
fonts. These cover signed out, waiting, reported usage, partial usage, uncertain,
error, unsupported, disconnected and loading, with light/dark/1.8x variants and
scrolled details for the main sign-in/usage surfaces. The code in these captures
is invented. No real code/account data was captured.

Capture logs and PNGs are ignored local evidence. Validation logs use
`build/traycer/account-*.log`. No account source was edited in another checkout.
The coordinator owns integration, native validation and release.
