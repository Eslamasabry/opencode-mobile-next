# Development services — 2026-09-08

Branch `feature/development-services`, baseline `5e1cc82`.

## Finish line and scope

From Workspace → Manage project → Development services, save a project command
and reachable preview URL, explicitly start it, inspect command status and
bounded logs, visit its reviewed URL, stop/restart only its owned command,
recover configuration and ownership after app restart, and remove local data.

Non-goals: arbitrary process discovery/adoption or PID control, tunnels/public
binding, package installation, automatic startup, server credential storage,
LSP/formatter installation, managed hosting or SaaS provisioning.

## Callable mechanism: verified before enabling controls

The pinned beta contract exposes location-scoped `POST /api/shell` with
`{command,cwd,timeout,metadata}`, exact-ID `GET /api/shell/{id}`, byte-cursor
`GET /api/shell/{id}/output`, and exact-ID `DELETE /api/shell/{id}`.
The existing managed-shell gateway already implemented the read/output/delete
methods. This feature adds POST and maps only its ownership metadata field.

An owned Android-emulator fixture verified the installed official
`/opt/oc-qa-v2/npm/bin/opencode2`, reporting `0.0.0-beta-18600`, inside
`oc-qa-lean-20260907`. The new fixture used `/opt/oc-qa-dev-services` for
HOME/XDG/project and bound only `127.0.0.1:4138`; the port was checked unused.
It generated a synthetic password inside the guest, retained privately in a
curl config and server log. No accounts, providers or model endpoints were used.

Observed results (owned script exit 0):

| Check | Result |
|---|---|
| Unauthenticated shell read | HTTP 401 |
| Three POSTs with per-attempt owner metadata | HTTP 200; metadata, command, cwd and start time round-tripped |
| Two foreground `printf ready-log; sleep 120` commands | Both reported `running`; no exit code invented |
| `printf failed-log; exit 7` | Reported `exited`, exit code 7 |
| Output reads with limit 1024 | Expected text, valid absolute byte cursor and size |
| Delete first owned ID | HTTP 204, subsequent exact-ID GET 404 |
| Read second owned control command after first deletion | Still running |
| Cleanup | Only three recorded shell IDs deleted; exact spawned server PID killed/waited |
| Separate cleanup verification | Server PID absent; port 4138 no longer responds |

Safe host log: `build/traycer/dev-services-proof.log`. Non-secret reproduction
scripts: `build/traycer/prove-dev-services.sh`, `oc-qa-dev-services.sh`, and
`check-dev-services-cleanup.sh`. These local helpers are ignored by Git; private
guest auth/log files are neither copied nor committed. Runtime lease was released
immediately after proof and cleanup verification.

## Ownership and recovery

- Save configuration first. Each explicit Start writes a random ownership nonce
  locally **before** POST and sends it as `metadata.ocDevelopmentServiceOwner`.
- The receipt includes the returned `sh_` ID and start time. Control re-fetches
  and compares nonce, ID, start time, command and cwd before deletion. A matching
  command or PID alone never establishes ownership.
- A lost POST response keeps an unknown attempt. Only one running shell carrying
  that already-saved nonce can recover its ID; there is no arbitrary adoption.
  If recovery cannot confirm it, Start stays unavailable until an explicit
  "Forget last run" confirmation acknowledges duplicate-process risk.
- Stop requires exact-ID deletion and a confirming missing record. Restart
  performs that sequence before a fresh owned Start. Server deletion removes
  its retained log; confirmations explain that effect.
- Status describes the command, not application readiness or URL reachability.
  Network/identity failures are Unknown. The feature never invents an exit code.
- Logs request at most a 64 KiB tail and render as plain text after control-code
  removal. They are not persisted. URLs are entered explicitly, never extracted
  or rewritten from logs, and always pass through `openExternalLink`.
- `oc.developmentServices.<profileId>` stores configurations and ownership
  receipts only. The existing profile preference sweep covers it. Writes check
  profile availability before/after persistence; late receipts cannot recreate
  deleted profiles or service definitions.
- Remove configuration and profile deletion affect local data only. They never
  kill server commands; the service-removal confirmation says to Stop first.

V2 enables `ServerCapabilities.developmentServices`. V1, Codex and demo retain
the default false: the honest subset is saved commands, Copy and reviewed Visit.
No unsupported start/stop control is shown.

## Flutter checkpoint validation

Validated using the pinned Flutter 3.47.2 helper, on the feature diff from
`5e1cc82`, under an exclusive runtime lease. No full suite was requested.

| Command / check | Result |
|---|---|
| `flutter pub get --offline` | Dependencies resolved; exit 1 only for the existing Windows plugin-symlink/Developer Mode prerequisite. Machine settings unchanged |
| `flutter gen-l10n` | Passed |
| `dart format` on changed Dart files | Completed |
| `flutter test --no-pub --concurrency=1 test/development_services_test.dart test/development_service_gateway_test.dart` (first two files of the focused run) | 15 passed |
| `flutter test --no-pub --concurrency=1 test/development_services_screen_test.dart` | Final run: 9 passed, including all capture journeys |
| Serial `managed_shell_gateway_test.dart`, `shell_output_test.dart`, `profile_deletion_test.dart`, `l10n_coverage_test.dart` | 32 passed |
| `flutter analyze --no-pub` | No issues, 19.4 seconds |

Total: **56 focused and related checks passed**. Initial screen runs found a
missing capture-helper argument, ambiguous confirmation finder, and unpumped/
lazy scroll targets in the test harness; these were corrected and the screen
file rerun. Analyzer style/documentation findings were fixed without ignores.
The only subsequent controller/store edits were equivalent brace formatting
and documentation; their already-passing behavioral cases were not repeated.

Coverage includes save without execution, restoration, explicit start/stop,
restart, bounded output, failed refresh becoming unknown, exact owner mismatch,
lost POST response recovery, scope changes during resolution/control, late
receipts after deletion, malformed storage, unsupported controls, reviewed HTTP
Visit, project navigation, and reconnect reconciliation without a second Start.

Fifteen synthetic widget captures are in
[`docs/qa/development-services-2026-09-08`](../qa/development-services-2026-09-08).
They cover saved/running/log/unknown states in light and dark at 390 logical
pixels, and 320 pixels with 2× system text scaling (including modal sheets).
Visual inspection found wrapping/scrolling without horizontal overflow; the
large-text app-bar title uses its normal ellipsis. Buttons retain text labels,
icon-only actions have tooltips, and status has a text label in addition to color.
The screenshot fixtures use fictional commands, URLs and logs.

Representative captures:

- [Running, light](../qa/development-services-2026-09-08/light-running.png)
- [Unknown with recovery guidance, dark](../qa/development-services-2026-09-08/dark-unknown.png)
- [Running controls, 320px / 2×](../qa/development-services-2026-09-08/large-running.png)
- [Log sheet, 320px / 2×](../qa/development-services-2026-09-08/large-logs.png)

Logs remain in ignored `build/traycer/services-*.log`. These are local focused
checks and widget captures, alongside the separate native contract proof above.
The complete Flutter-on-device service journey, repository full suite,
integration review, native build, deployment and release remain unclaimed.
