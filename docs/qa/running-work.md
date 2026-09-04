# Running work: command management and agent navigation

Date: 2026-09-05. Pinned Flutter 3.47.2 / Dart 3.13.2.

## What changed

- A compact Running work indicator replaces the growing agent-chip strip in chat.
  It opens one sheet with related agents and currently running commands.
- Agent rows open the related conversation. Command rows open a readable,
  selectable output viewer with Copy output and Follow output.
- Output uses the server's absolute byte cursor, including multibyte text.
  The viewer bounds its retained text to 128 Ki characters and labels trimming.
- Scrolling back pauses Follow. A visible viewer can load more output or refresh
  manually. Polling pauses behind dialogs, on app backgrounding, and on close.
- Change timeout offers 1, 5, 15, or 60 minutes from now, or clears the timeout.
- Stop requires confirmation that it terminates the command and removes retained
  server output. Text already loaded in the viewer remains readable until close.
- Runtime discovery hides unsupported shell management. OpenCode 1 keeps agent
  navigation; its PTY terminal is independent of these managed commands.
- Shell lifecycle events trigger reconciliation. Reconnects preserve the output
  cursor; stale responses from a retired transport cannot overwrite current data.
- A missing command displays an unavailable state. A changed server process
  identity provides evidence for the more specific server-restart explanation.
- Workspace or server changes disable the old viewer's controls. Completed work
  remains in the conversation; this is not a persistent job-history store.

## Protocol and scope

HTTP tests cover the repository's `opencode2-openapi-beta-18600.json` contract:
scoped list/detail/output reads, byte cursor and page limit, timeout replacement
in milliseconds, clearing with zero, and shell deletion. Discovery distinguishes
404/405/501 from authentication and server failures. Negative discovery is cached
while the sheet is open and reconsidered after reconnect or manual refresh.

Commands are associated through explicit session metadata or shell IDs in the
conversation's tool parts. Unattributed workspace commands are omitted. The UI
model does not expose the server's capture-file path, working directory, or PID.

`shell.created`, `shell.exited`, and `shell.deleted` are freshness hints, not
synthetic messages or a durable log. Session-shell changes rehydrate the real
conversation. A reconnect alone never proves cancellation or a server restart.

## Test and iteration evidence

The final affected regression group passed 130 checks, including 24 new
running-work checks and two localization checks. Added cases cover malformed
capture resets and reconnects occurring while a dialog hides the viewer. Coverage
includes:

- Byte cursors, output reset, bounded memory, escape-sequence removal, and retry.
- Unsupported routes, authentication failures, scope parameters, and v1 isolation.
- Related-agent navigation and filtering commands belonging to other sessions.
- Stop confirmation, timeout replacement/clearing, and preserving loaded output.
- Visibility and app lifecycle, including a reconnect deferred until visible.
- Missing commands, server restart evidence, and workspace-change controls.
- 360 dp layouts with 2.5x text, including scrolling to controls.

Native review increased output text size and contrast and made Stop's destructive
effect visually distinct. Stopped commands no longer show a redundant status or
an ineffective Follow switch. A reset-page regression led to validating a new
page before discarding previously loaded output. A reconnect-under-dialog test
caught a lazy initialization bug: scope and refresh revision are now captured in
`initState`, so the first reconnect is detected reliably.

## Native visual fixture

The Android screenshots use the actual production widgets with controlled sample
data from `test/preview/running_work.dart`. Its in-memory preferences and fake
repository issue no real shell commands and leave stored app drafts intact.
Normal builds use `lib/main.dart`; the normal app is rebuilt and reinstalled after
the visual check. These images verify native layout and interaction, while the
HTTP tests verify the v2 wire calls. They are not a live OpenCode 2 server test.

| Native interaction | Screenshot |
| --- | --- |
| Related agents and running commands | [Running work](running-work/01-sheet.png) |
| Readable output and controls | [Output](running-work/02-output.png) |
| Timeout choices | [Timeout](running-work/03-timeout.png) |
| Stop confirmation | [Confirm Stop](running-work/04-stop-confirmation.png) |
| Loaded output retained after Stop | [Stopped command](running-work/05-stopped.png) |
| Normal app and draft restored after the fixture | [Restored composer](running-work/06-normal-app-restored.png) |

## Verification

- `flutter analyze --no-pub`: clean, no new lint ignores.
- Final affected regression group: **130 tests passed**.
- Normal `lib/main.dart` x86_64 debug APK built and installed on API 36 without
  uninstalling or clearing app data. After reloading, Local QA reconnected to the
  isolated OpenCode 1.18.28 server and the synthetic composer draft was restored.
- Native sample timeout selection and confirmed Stop completed successfully;
  loaded output remained visible after Stop.
- Normal APK SHA-256:
  `9e6e36c3dfd2678ee32d21c84ecbe684340c60d371f48d8f855558cc79c62d47`.

CI runs the full application and SDK suites plus Android, Linux, and Windows
release builds for the pushed commit. Its result is recorded in PR #63.
