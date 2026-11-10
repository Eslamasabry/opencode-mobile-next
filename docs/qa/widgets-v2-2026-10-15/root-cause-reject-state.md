# Conversations widget after Reject: STALE

Branch: `codex/widget-reject-state`; base: `4203e6fba` (1.4.0 release tree).
Current finish line: an app-closed widget action returns to honest on-demand
status, finite follow-up checks settle a longer turn, and all widget sizes show
useful content with verified release-emulator evidence.
Non-goals: unlimited background monitoring, permission-policy changes,
physical-device qualification, publishing or release.

## Server ground truth

**STALE**, reproduced against actual local scratch servers and real free-model
tool requests, without opening the app. This establishes the tested rejection
behavior; it does not claim every model or future server version must stop.

| Server | Status endpoint | Before Reject | First observation after accepted Reject |
| --- | --- | --- | --- |
| OC2 stable 2.0.29 | `GET /api/session/active` | `running`, one shell permission | absent from complete active map (idle), no permission, within 257 ms |
| OC1 1.19.0 | `GET /session/status` | `busy`, one bash permission | idle, no permission, within 294 ms |

The OC2 assistant ended with `finish=error`, `error.type=aborted`,
`Step interrupted`; the shell tool had `executed=false` and the error
`The user declined this tool call`. OC1 likewise recorded a rejected tool.
Sanitized initial timelines: [OC2](reject-probe-oc2.json) and
[OC1](reject-probe-oc1.json). Those initial prompts asked the model to finish
if rejected. The [unbiased OC2 repeat](reject-probe-oc2-repeat.json) and
[OC1 repeat](reject-probe-oc1-repeat.json), whose prompts only requested
`sleep 50`, became idle within 257 ms and 294 ms respectively; these are the
primary observations in the table. See [method and reproduction commands](reject-probe-method.md).
Timing is an upper bound from approximately
250 ms status sampling, not an exact server transition timestamp.

The PATH launchers failed because their postinstall binaries were missing.
Official versioned binaries were extracted into private temporary directories;
no npm installation or home package edits were made. OC2 listened on
`127.0.0.1:4097`, OC1 on `127.0.0.1:4098`, with isolated config/data and the
free `opencode/big-pickle` model. OC2 Basic user was `opencode`; its per-run
password was handled privately and is absent from committed evidence.
Both owned servers were stopped by exact PID and their ports checked free.

## Root cause

[`WidgetActionRunner`](../../../lib/background/widget_action_runner.dart)
previously negotiated health, fetched the permission, sent the decision,
removed the request ID, and immediately closed its transport. It never read
status after the reply and retained `busy=true` and `startedAt` from the old
snapshot. Stop instead optimistically forced idle without checking the server.
The headless runner has no app controller or SSE subscription to publish a later
idle event after closing.

[`SessionWidgetRow`](../../../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SessionWidgetRow.kt)
then started its chronometer using cached busy/start time regardless of whether
the snapshot was connected. Thus a stale request row could say “Working” with
a ticking timer indefinitely, despite displaying disconnected copy elsewhere.

## Initial change and bounds (superseded by build 2329 below)

After an acknowledged permission reply or abort, the runner reads the existing
domain gateway's status endpoint immediately, then every 250 ms while busy or
retrying, for at most five seconds. It reserves 500 ms within the original
30-second native lease for settlement. Confirmed idle (including absence from
the complete active map) clears busy, start time, step, and the Stop control.
The target row and counts are published through the existing native whitelist.
Transport cleanup still runs before returning the result.

A failed, hung, or malformed status read cannot establish idle or undo the
acknowledged command. The result stays accepted, cached busy is retained, and
the snapshot is detached. Scope/profile ownership checks still fence reads and
publication; uncertain mutations are never replayed. Native clears the old
step alongside start time on an idle patch and only runs a timer while truly
connected. Disconnected rows reuse existing localized connection copy.

This is finite reconciliation attached to a tap, not a background poller or
reconnect loop. A genuinely long turn outlasting five seconds remains cached
and visibly disconnected, without a live timer; its eventual idle state needs
the next app/live connection. No claim of unlimited app-closed monitoring is
made. The observed Reject defect settles inside the reconciliation window on
both tested servers. Stay connected, privacy-safe routing, native lease length,
and mutation deduplication remain unchanged; no storage migration is needed.

## Verification

Pinned Flutter 3.47.1 and Dart, with heavy commands under
`tool/qa/machine_lock.sh` and serial Dart tests. Native checks use Temurin 17.

- Failing-first runner tests: 31 passed, **21 failed** before reconciliation.
- Final focused run: **87 passed** across `widget_action_runner_test.dart`,
  `widget_action_runner_http_test.dart`, `widget_snapshot_test.dart`, and
  `widget_v2_contract_test.dart`. Production HTTP clients exercise OC1 and OC2
  stable/beta routes, both permission decisions, busy-to-idle reconciliation,
  exact request ownership, and duplicate suppression.
- Timeout coverage includes a hung status read, late completion, and less
  than 500 ms remaining before the native deadline; all retain accepted
  acknowledgement without replay.
- `flutter build apk --release --config-only`: passed.
- `cd android && ./gradlew :app:testReleaseUnitTest --no-daemon --max-workers=2`
  under the build lock with Temurin 17: **461 tests, 30 suites, zero failures,
  errors, or skips**, BUILD SUCCESSFUL in 4m 27s. The owned single-use Gradle
  daemon exited; its exact PID was checked absent. An initial invocation using
  the host OpenJDK was stopped by its exact owned client/daemon PIDs before
  rerunning with the pinned vendor. Existing native/plugin deprecation warnings
  and untranslated-language notices remain.
- Reproducible Python probe: syntax compilation and actual unbiased OC1/OC2
  executions passed; both listeners were checked absent after cleanup.
- `flutter analyze --no-pub`: **No issues found** (142.7s). Two null-aware
  collection style infos from the initial run were fixed without suppressions.
- Changed Dart format, documentation link checks, and `git diff --check`: passed.
- `OC_PREFLIGHT_JOBS=1 tool/qa/preflight.sh HEAD~1`: **OK**. Commit/format
  and generated localization checks passed; 798 QA tool tests ran (two existing
  skips), and all 161 repository-scanning tests passed. The only subsequent
  amendment records these results in this document; implementation and test
  files are unchanged. Commit rules and diff/link checks were rechecked after
  that documentation-only amendment.

Reproduce focused application checks with the pinned Flutter directory on PATH:

```sh
export OC_TEST_SLOTS=3
export PATH="$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin:$PATH"
tool/qa/machine_lock.sh test -- flutter test --no-pub --concurrency=1 \
  test/widget_action_runner_test.dart test/widget_action_runner_http_test.dart \
  test/widget_snapshot_test.dart test/widget_v2_contract_test.dart
tool/qa/machine_lock.sh test -- flutter analyze --no-pub
tool/qa/machine_lock.sh build -- flutter build apk --release --config-only
# Set JAVA_HOME to the installed Temurin 17 directory first.
tool/qa/machine_lock.sh build -- bash -c \
  'cd android && ./gradlew :app:testReleaseUnitTest --no-daemon --max-workers=2'
OC_PREFLIGHT_JOBS=1 tool/qa/preflight.sh HEAD~1
```

The initial fix verification above did not include an emulator or signed APK.
No repository-wide Dart suite, physical-phone test, release, push, or PR was
performed. The subsequently authorized emulator proof follows. The visual change only removes
misleading disconnected animation; it adds no controls or accessibility labels.


## Follow-up: successful lease incorrectly offered background recovery

The 2327 proof established server settlement but **failed presentation**: both
six-second frames said “Background settings / Background connection unavailable”.
Those captures and the proof table below are superseded by the 2328 rerun.

The cause was native display policy, not a failed request, `recoveryRequired`,
or the service stop path. `SessionWidgetSnapshot.connection()` derived
`onDemandAvailable` from whether any row still had an advertised, valid action.
Successful settlement removed that last action. With Stay connected off and
the bounded service stopped, the policy therefore selected `BACKGROUND_SETTINGS`.
An accepted receipt does not require recovery; the transient service's
`onDestroy` does not invalidate a live owner's connection.

`WidgetV2Policy.onDemandAvailable` now uses the version-2 on-demand descriptor
independently of available buttons. A settled conversation returns to
`ON_DEMAND`, preserving its title and “Connects when you tap” copy. Actual
controls still require supported, advertised actions and current identity.
Missing descriptors, unsupported schemas, and explicit unsupported-action
receipts retain their recovery behavior. No service lifetime or permissions
were broadened.

Failing-first policy coverage reproduced **3 failures out of 18** with the old
action-dependent predicate. The fixed policy passed all 18. The final release
Kotlin suite passed **463 tests in 30 suites, zero failures/errors/skips** using
pinned Flutter config-only, Temurin 17 and the build lock; Gradle completed in
3m 27s and its owned single-use daemon exited. Tests cover accepted Allow once,
Reject and Stop through pending → settled → lease ended, plus absent/invalid
action identity, descriptor/schema rejection, recovery and stopped timers.

The large blank area under a single conversation in the **2×2 card is expected
for the current layout**. Its `ViewFlipper` fills the remaining height using
weight 1, while the conversation row is top-aligned and wraps its content;
the action strip also reserves 32 dp. One settled conversation without tasks
or todos does not fill that space. This fix does not redesign the card.

## Emulator proof — local release APK 2328

**FAIL overall: Reject passes; Allow once exposes remaining failures.**
Pixel_6 / `emulator-5554` only, 2026-10-15. This replaces all six 2327 PNGs.
The tested implementation is exactly `3e2a18e9c`; no implementation changes
were made during the proof. The requested settled Allow result was not proven.

Built with pinned Flutter using `flutter build apk --release --build-number=2328`
under the build lock, Temurin 17, two Gradle workers and a 3 GB heap.
Release build and bundled phone-engine verification passed (246.6 s).
APK version: **1.4.0 (2328)**, 105025594 bytes.
The replacement matches the existing local signing certificate
`1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`;
`adb -s emulator-5554 install -r` succeeded without clearing data.
APK SHA-256: `b63d2fa9a89373283480da28c9f723b62fa9ed04ea6ad39a6060becd1e9648ec`.

Scratch OC2 **2.0.29** listened on host loopback port 4097, reached through
`10.0.2.2`. Actual Big Pickle shell requests used **Ask first** (read back as
all permissions `ask`) and **Stay connected off** (absent preference defaults
false). The app received each permission in the foreground. The emulator's
preexisting built-in server auto-started on app launch; it was stopped through
its UI before each retained capture. Home followed by `am kill` then closed
the app process, without force-stop. **`pidof` confirmed no app process before
both retained widget taps.** Only launcher controls answered requests; no
snapshot writes or synthetic permissions were used. An initial Reject attempt
with the built-in service still alive was discarded and repeated cold.

Historical build 2328 captures are preserved in commit `47c8b6ddb`; the
canonical image names below are replaced by build 2329 evidence later in this report.

| Action | Before | Immediately after tap | Six seconds after tap |
| --- | --- | --- | --- |
| Reject — PASS | `reject-emulator-reject-before.png`: 1 running / 1 need you, title, both controls | `reject-emulator-reject-after.png`: first frame still pending; server already idle, request cleared | `reject-emulator-reject-6s.png`: **0 running / 0 need you**, title, **Connects when you tap**, no controls or timer; server idle |
| Allow once — FAIL | `reject-emulator-allow-before.png`: 1 running / 1 need you, title, both controls | `reject-emulator-allow-after.png`: first frame still pending; server running, request cleared | `reject-emulator-allow-6s.png`: 1 running / 0 need you, title, **Background connection unavailable / Background settings** in row; server still running |

All six images were visually inspected and are unedited `adb exec-out screencap -p`
captures. Immediate capture starts as the tap command returns; the final capture
starts at +6.000 s on a monotonic clock. Before frames already use disconnected
copy and have no live timer. The final Allow prompt requested shell `sleep 0`
followed by only “done”; it still outlasted the reconciliation window.

### Remaining failures found during the 2328 proof

The global connection-policy fix works for the settled Reject row, but the
independent `SessionWidgetRowState.rowNeedsSettings` predicate still maps an
on-demand row to recovery when it has candidate actions and none are both
valid and advertised. The final Allow snapshot had `busy=true` and
`onDemandActions=[]`. That triggers the row fallback even though the permission
was successfully answered. The header remained a normal count; the **row**
still violated the requested no-recovery behavior. The policy-only regression
does not cover this renderer override. Why the accepted busy snapshot lost its
Stop advertisement requires follow-up at publication/action-proof construction;
this proof does not claim that cause is resolved.

Two earlier cold Allow attempts are preserved as sanitized status observations
in [reject-emulator-evidence.json](reject-emulator-evidence.json). In the first,
OC2 was still running at six seconds and the widget correctly showed on-demand
copy with Stop. In the second, OC2 was already idle at the six-second read but
the widget retained **1 running / Stop**: the server finished after the runner's
five-second reconciliation cutoff. Both later finished normally. This is an
observed stale-state limitation, not merely a hypothetical long-turn caveat.
The final attempt also eventually finished with exit 0 and assistant `stop`,
after the bounded transport had closed. No additional automatic idle refresh
was demonstrated with the app closed. The screenshots/table intentionally
retain the final failing attempt rather than presenting a selected pass.

Read-only native inspection after the final action found `connected=false`,
`runningCount=1`, `pendingCount=0`; Allow remained `busy=true` with no advertised
actions, while Reject was idle. Stay connected remained false. There were no
app services after the captured actions, and the launcher stayed visible.
Server transcript checks found rejected shell calls aborted and all three
allowed shell calls completed with exit 0. Later server reads found both
sessions idle with no pending requests. This run proves successful request
delivery, but **does not qualify Allow settlement or recovery presentation**.
The 2×2 whitespace explanation above remains unchanged; no redesign was made.

The emulator (PID **960677**) and scratch OC2 server (PID **959867**) were
stopped by exact PID and checked absent; port 4097 was checked free. Owned
Gradle daemons **961064** and **962731** exited and were checked absent.
The emulator lock was held for the entire session and released after shutdown.
No physical device, home-prefix package installation, push, tag, public release,
or signing-key change occurred.

`OC_PREFLIGHT_JOBS=1 tool/qa/preflight.sh HEAD~2` passed across the native fix
and evidence commits: commit rules, formatting, generated localization, 798 QA
tool tests (two existing skips), and all 161 repository-scanning tests. JSON,
local documentation links, `git diff --check`, and a scratch-password leak
check passed. This final amendment only records those results; the tested
implementation and six captured images are unchanged. Passing automated gates
does not override the failed Allow emulator qualification above.


## Build 2329 — bounded settlement and useful widget content

The 2328 failures above drove two changes. An accepted action that remains busy
now schedules three ordinary WorkManager one-offs, with minimum cumulative
delays of roughly 30 seconds, two minutes and ten minutes. Each status read has
a 15-second deadline. Android may defer ordinary work; these are minimum delays,
not exact alarms. Idle, a changed turn, removed profile, changed scope, or the
last check ends the chain. No follow-up foreground service, SSE subscription,
periodic polling, or mutation retry is used.

A private profile-scoped ticket binds each check to its original conversation,
turn marker, start time, target fingerprint and project scope. The worker reads
server status and permission state through the existing gateway and rechecks
identity before publishing. Busy on-demand rows say “Working · last known”,
without a timer. Missing Stop proof hides the button instead of changing the row
to background recovery. Idle clears the running state and updates the safe
assistant preview.

The 2×2 conversation card now uses its height for the project basename, title,
and up to three lines of safe assistant text or latest step, with status and
actions at the bottom. The previous empty area was a consequence of the old
wrapping row and is superseded by this redesign. No prompt, tool arguments,
reasoning or raw project path is added to the snapshot. Compact and wide layouts
use the same data and status rules. Usage defaults to the first measured limit
when no explicit selection exists, including the per-profile fallback when the
unified feed has no measured readings. Saved selections remain authoritative.

The initial release candidate exposed a WorkManager Room constructor removed by
release shrinking; unit tests alone did not catch this startup failure. Release
startup and natural background execution are required in the final proof below.

The first natural app-closed follow-up also exposed an OC2 protocol edge:
a terminal `idle` timeline message is adapted into a tagged user-shaped notice.
The shared turn-marker helper previously treated that notice as a new prompt,
so the worker rejected its own original turn as stale. The helper now uses the
transcript's existing genuine-prompt criterion and excludes tagged OC2 timeline
records. HTTP regression coverage includes the actual stable idle/system shapes,
while a genuine subsequent user message still invalidates the old ticket.

Visual review before the final build additionally caught wallpaper-muted text
using a cached app theme, excess space in wide idle rows, and a clipped preview
at 150% text. Those findings are part of the final visual acceptance, rather
than being accepted as successful screenshots.

The final visual review also found the wide card's fixed-width Style control
clipping at 150% text. Its width and height now follow the text, with the old
dimensions retained as minimums. The same review found a literal `</think>`
marker in an actual server text part. The shared preview boundary now removes
paired, nested and unfinished `<think>` / `<thinking>` blocks before truncation;
an orphan closing marker discards its preceding fragment and preserves the
following reply. Four regressions cover these cases and unrelated markup.
Typed reasoning was already excluded. This handles explicit known delimiters;
it does not infer untagged or malformed reasoning.

Usage's single-provider screenshot intentionally retains spare capacity for
additional limits. The scratch server's free model exposes no quota reading,
so the visual fixture is explicitly labelled “Demo limit · fixture”; it is not
account usage evidence. No explicit widget selection is saved, and the unified
feed is empty, exercising the default measured per-profile fallback. The
fixture is removed after capture. The Conversations preview comes from an
actual assistant reply in the scratch project, describing a review plan rather
than claiming completed QA.

### Visual acceptance matrix

Every image is an unedited launcher capture from `emulator-5554`. The review
checks useful content, complete lines, contrast and reserved colours, rather
than treating file creation as visual acceptance. The light/dark captures
explicitly refresh the providers after changing Android night mode.

| Surface | Light | Dark | Empty-space and legibility judgment |
| --- | --- | --- | --- |
| Conversations 1×1 | [light](reject-emulator-2329-conversations-1x1-light.png) | [dark](reject-emulator-2329-conversations-1x1-dark.png) | Compact title and status fit; intentional title ellipsis preserves status. |
| Conversations 2×2 | [light](reject-emulator-2329-conversations-2x2-light.png) | [dark](reject-emulator-2329-conversations-2x2-dark.png) | Project, two-line title and three-line reply use the body; settled status stays at the bottom. The modest separation above status is intentional, replacing the previous mostly empty card. |
| Conversations 4×2 | [light](reject-emulator-2329-conversations-4x2-light.png) | [dark](reject-emulator-2329-conversations-4x2-dark.png) | Two idle conversations fit at normal text size; each has a readable preview and status. |
| Usage | [light](reject-emulator-2329-usage-light.png) | [dark](reject-emulator-2329-usage-dark.png) | First measured fixture limit, percentage, progress and reset appear without configuration. Spare height with one provider is expected; no extra quota is invented to fill it. |

Additional captures cover [1×1 at 150% text](reject-emulator-2329-conversations-1x1-large-text.png),
[2×2 at 150%](reject-emulator-2329-conversations-2x2-large-text.png),
[4×2 at 150%](reject-emulator-2329-conversations-4x2-large-text.png),
[Arabic/RTL](reject-emulator-2329-conversations-rtl.png), and
[blue tinted accent](reject-emulator-2329-conversations-accent.png).
At large text the card reduces preview/row density to preserve whole lines and
status; the wide card displays one full row. Arabic chrome follows RTL while
the English assistant content remains LTR. Wallpaper and explicit accent
styles preserve contrast. Green/neutral usage colours do not imply a permission
alert; amber is reserved for needs-you states. Red is not used by these widgets.

### Final proof method

The action proof uses actual OC2 2.0.29 permission requests and server status,
with all permissions set to `ask` and Stay connected off. The app first hydrates
the request. Its preexisting built-in server is stopped through the service's
normal stop action, then Home and an exact-PID process kill close the app.
`pidof` must confirm absence before each launcher tap. Force-stop is not used
during the functional proof because it suppresses Android scheduled work.

Reject is sampled immediately and at six seconds. Allow requests a shell
`sleep 12`, keeping the turn busy beyond the initial five-second lease check.
At six seconds its newly started app process is killed again by exact PID;
subsequent samples observe the launcher and server without reopening the app,
forcing jobs, changing clocks, answering through the API, or patching the
Conversations snapshot. The existing bounded action lease may briefly use its
foreground service; the later status reads use ordinary WorkManager execution.

Server observations use `GET /api/session/active` and
`GET /api/permission/request`. Native observations record only the detached
connection flag, target row, aggregate counts, follow-up ticket count and
foreground-service presence. Reads of the private preference file and work
database are not copied wholesale into the report. The Usage-only fixture is
separate visual evidence and does not establish live account quota retrieval.


### Final build 2329 results

Verified release APK: `1.4.0 (2329)`, 105,144,104 bytes, SHA-256
`d1f04a57176e2515750df24205185bd7dd38bca0556cd8b1f72c8f6623a82796`.
The signer matches the authorized local release certificate
`1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`.
All action and visual captures below use this final APK on Pixel_6,
Android 14 / API 34, exclusively through `adb -s emulator-5554`.
The canonical six screenshots replace the earlier 2328 evidence.

| Action / observation | Server truth | Widget result | Screenshot |
| --- | --- | --- | --- |
| Reject before | Running; one pending permission | Needs you; Reject available | [before](reject-emulator-reject-before.png) |
| Reject immediately after tap | Subsequent read: idle; no pending permission | Immediate frame still shows Needs you; native snapshot already settles during readback | [after](reject-emulator-reject-after.png) |
| Reject 6 s | Idle; no pending permission | Ready; Connects when you tap; no timer/recovery | [6 s](reject-emulator-reject-6s.png) |
| Allow once before | Running; one pending permission | Needs you; Allow once available | [before](reject-emulator-allow-before.png) |
| Allow once immediately after tap | Running; permission answered | Immediate frame still shows Needs you while bounded action lease completes | [after](reject-emulator-allow-after.png) |
| Allow once 6 s | Still running; no pending permission | Working · last known; Connects when you tap; no live timer/recovery | [6 s](reject-emulator-allow-6s.png) |
| Allow once 20 / 40 s | Idle; no pending permission | Last-known Working until Android schedules the bounded check | [20 s](reject-emulator-2329-allow-20s.png), [40 s](reject-emulator-2329-allow-40s.png) |
| Allow once 65 s | Idle; no pending permission | Ready, no actions, no follow-up ticket; app never reopened | [65 s](reject-emulator-2329-allow-65s.png) |

Screenshots are captured before the sequential server/native readback; the
immediate after-tap frame can precede that readback's settled state. The six-second
and later frames establish visual convergence.

The process was absent before both taps and killed again after Allow's six-second
capture. There was no foreground service at six seconds or any later sample.
The idle settlement is natural WorkManager execution, not an injected snapshot
or forced job. Android scheduling is inexact: 30 seconds is the first requested
minimum delay, not a guaranteed completion deadline. The read-only work database
check found zero queued, running or blocked follow-ups after settlement. Three
terminal FAILED work records are expected because the worker deliberately fails
the dependency chain when idle, preventing its later checks from running; this
is not an action failure. Whitelisted observations are in
[reject-emulator-2329-evidence.json](reject-emulator-2329-evidence.json).

Validation on the final frozen source:

- VPS release build and R8 reflective-constructor verification passed.
- Pinned Flutter release config-only, then `:app:testReleaseUnitTest --no-daemon`:
  497 tests across 33 suites, zero failures, errors or skips.
- `tool/qa/vps_test.sh` with concurrency 3: all 150 tests in nine affected Dart
  files passed (action unit/HTTP, status unit/HTTP, snapshots, contracts,
  profile deletion and Usage snapshot/settings).
- Changed Dart formatting, four release-rule Python tests and `git diff --check`
  passed. The full Dart suite was not run for this candidate.

- Full VPS `flutter analyze` passed with no issues (309.9 seconds).


Final visual review accepted all 13 size/theme/accessibility captures with no
blocking clipping, contrast or empty-space issues. The explicit Usage fixture
was removed, font scale restored to 1.0, and app locales restored to default.
Emulator PID `1193378` and scratch OC2 PID `1003801` were stopped by exact PID
after identity checks; both are absent and port 4097 is free. The emulator lock
was released. Owned VPS Gradle PIDs `1247978` and `1296403` also shut down and are
absent. The coordinator's build-script change is preserved and excluded from
this feature commit. No physical device was accessed and nothing was pushed.


The first preflight caught a missing preference-ledger classification for
`oc.widgetFollowup.`. It is now classified as profile-scoped, matching the
existing deletion sweep and cancellation path. This is test bookkeeping only;
the 52 frozen production/test source files used for the APK and focused checks
remain unchanged.


Final `tool/qa/preflight.sh HEAD~1` passed on candidate `b0625bf28` in an
isolated full-history VPS checkout, with concurrency 3: commit rules and
formatting (one commit, 17 Dart files), generated localization consistency,
802 QA-tool tests (13 expected skips), and all 161 repository-scanning tests.
The corrected preference-ledger file separately passed all five cases.
Only this result paragraph was added afterward; APK source and captures remain
unchanged. Local log: `/home/owner/Storage/tmp/widreject-2329-resource-final/preflight-fixed.log`.

## Redesign proof on emulator build 2353

Release build 2353 on the Pixel 6 emulator with a scratch OpenCode 2 server; the
app was force-stopped before the widget tap and Stay connected was off. Changes
made after the coordinator's review of 2352:

- Markdown is stripped from every widget preview line in the snapshot publisher
  (`stripWidgetMarkdown`; backticks, emphasis, headings, list and quote markers,
  link addresses), with tests.
- The header shows only non-zero counts ("1 running", "1 needs you") and a calm
  "All caught up" when nothing runs or waits.
- Theme rule: each widget follows its own saved Style. The default accent
  "wallpaper" uses the system light/dark palette; any other accent uses the app
  theme colours from the last snapshot. The earlier mismatch was test setup (the
  Conversations widget had a saved blue/tinted style, Usage had wallpaper). Both
  widgets are now captured with the same default rule (wallpaper, system theme).
- The lone Usage limit is centred in the remaining height by the layout
  (weighted, centred container) with larger type.
- The 2x2 card shows up to four lines of the last reply under the title, status
  anchored at the bottom. A project line that is a generic folder name
  ("project", "tmp", "workspace", "home", "root", "src") is hidden; here the
  scratch project folder really is called "project", so the line now disappears.

| Capture | Light | Dark | Verdict |
| --- | --- | --- | --- |
| Idle 2x2 + Usage | [light](redesign-idle-light.png) | [dark](redesign-idle-dark.png) | Calm header, no project line, both widgets same theme; this session had no reply text so the middle is empty by design |
| Permission waiting | [light](redesign-permission-light.png) | [dark](redesign-permission-dark.png) | Good; amber only for needs-you |
| Allow once, app closed, running | [light](redesign-running-light.png) | [dark](redesign-running-dark.png) | Good: "1 running", last-known label, no timer |
| Settled by itself | [light](redesign-settled-light.png) | [dark](redesign-settled-dark.png) | Good: four plain lines of the reply, Ready, no Markdown |
| 1x1 | [light](redesign-1x1-light.png) | [dark](redesign-1x1-dark.png) | Good |
| 4x2 | [light](redesign-4x2-light.png) | [dark](redesign-4x2-dark.png) | Good; two conversations with plain previews |

### Build 2354 follow-up (idle card content, one connection hint)

- An idle 2x2 card with no reply text now shows true facts: the model (from the
  conversation's choice, else the model selected for new messages; published as
  `model` by the snapshot publisher) and "Last active N minutes ago" (system
  relative time from the snapshot's `updatedAt`), plus an "Open conversation"
  action anchored at the bottom. Voice and Style stay in the header as before.
- "Connects when you tap" is shown once per widget as a footer instead of under
  every row or card.
- Re-captured on emulator release build 2354 in light and dark: idle 2x2
  (`redesign-idle-*`), settled 2x2 after Allow once with the app closed
  (`redesign-settled-*`), 4x2 (`redesign-4x2-*`). All judged good: no large empty
  area, no Markdown, amber only for needs-you. The 2x2 permission and running
  captures above are from build 2353 and unchanged apart from the footer.
- Caveat: when a conversation has no model of its own, the line shows the model
  currently selected in the app, which is what a new message would use.
