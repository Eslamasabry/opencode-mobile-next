# NOTIFY review fixes

Reviewed against worktree base `066a00be` on `native/fix-notify-2026-11-01`, 2026-11-01.
The requested `notify.md` is absent from this checkout; the checklist was read from
`git show 3bb7632e:docs/native/reviews/notify.md`. All six findings still applied to the base.
**Already fixed: none. Rejected: none.** Changes stay within `native/android/notifications/**`, plus this report.
The W1/W2 flat `Failure`/`Outcome` and core `ConversationRef` contracts are retained; no shared module was edited.

## Disposition and regression evidence

Each primary regression was run against the original production code before its fix. The single filtered run
(`--tests '*notify finding*'`) executed six tests and failed all six on behavioral assertions, after resolving the
sandbox-only Robolectric setup issue. Supplementary regressions cover additional interleavings.

| Finding | Disposition and change | Regression tests |
|---|---|---|
| 1 BLOCKER: notification redaction | **Fixed.** Sensitive prefixed environment names are matched; flags consume complete single- and double-quoted values. Fixtures use synthetic ordinary words, with permission and finished-notification rendering assertions. Before: the prefixed assignment remained visible. | `AlertShadeTest`: `B-SEC-106 notify finding 1 masks prefixed assignments and whole quoted values on rendered alerts` |
| 2 MAJOR: old answer mutates a replacement request | **Fixed.** Posted rows carry their request identity. Shade comparison and mutations share a process-wide lock, including different shade instances. Successful, gone, failed and timed-out answers use conditional dismissal/replacement. Missing or mismatched identity leaves the current row alone. Before: completing A canceled B. | `NotificationActionBodyTest`: `B-CHAT-074 notify finding 2 old answer completion preserves the replacement request` (suspended answer, real shade, success / NotFound / network failure) |
| 3 MAJOR: reply shutdown removes another owner's row | **Fixed.** An injected `StatusOwners` acquires/releases `Reply`. Only the last owner removes 4747; a remaining server owner receives a detached, re-rendered row. Live/sign-in owners sharing BackgroundConnectionService retain its foreground promise and service lifetime. Destruction releases ownership too. Before: the server row disappeared; a supplementary red test also caught stopping a service still needed by live/sign-in. | `ReplyWatchServiceTest`: `S11-4 notify finding 3 a server arriving during watch retains its row after reply leaves`; `S11-4 notify finding 3 live and sign-in co-owners survive timeout and destruction` |
| 4 MAJOR: chat-wide / unrelated dismissal | **Fixed.** `ReplyRun` identifies profile, conversation and turn. Delete intents have immutable per-turn identities. Dismissal validates the displayed, currently watched run and sole reply ownership. Other runs keep their collector; promotion suppression applies only to the dismissed turn. Before: an unrelated delayed swipe ended the watch. | `ReplyWatchServiceTest`: `S11-9 notify finding 4 an unrelated delayed dismissal preserves the current watch`; `S11-9 notify finding 4 dismissal names a turn and preserves other runs and owners` (two turns in one chat, another profile, stale delete intent, shared ownership) |
| 5 MAJOR: lost connection/navigation target | **Fixed.** Status content and ReplyStopper use core `ConversationRef`; Stop and activity intents retain both identifiers. Legacy unresolvable stops are ignored. Native PendingIntent identities also include the target, preventing Android from retargeting an already-held action when the row changes. Frozen action names, request codes and Stop URI base remain. Before: an ambiguous legacy Stop dispatched; a supplementary red test caught held-intent retargeting. | `NotificationActionBodyTest`: `B-ANDR-040 notify finding 5 refuses a legacy stop without a profile`; `B-ANDR-040 notify finding 5 status stop and failed-stop Open retain the exact profile and chat`; `B-ANDR-040 notify finding 5 a held status action keeps its target after another profile replaces the row` |
| 6 MAJOR: startup loses finished notices | **Fixed.** Replace transient alert events with retained `pendingAlerts: StateFlow<List<ReplyAlert>>`, stable moment IDs and acknowledgement. Subscribe/reconcile undispatched before acknowledging watch readiness; acknowledge shown or intentionally skipped moments. Blocked posts remain unhandled. Quiet-hours finished notices are consumed without later replay. Before: a finished event immediately after start was lost. | `ReplyWatchServiceTest`: `B-ANDR-040 notify finding 6 an immediate finished notice survives watch startup`; `B-ANDR-040 notify finding 6 pending moments reconcile on startup and acknowledged moments never replay` |

## Validation

All Gradle invocations run serially under the existing `/home/eslam/Storage/tmp/oc-build.lock`, from `native/`, with:

```bash
JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 \
ANDROID_HOME=/home/eslam/Android/Sdk \
./gradlew --no-daemon -Pkotlin.incremental=false --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8' \
  -I /tmp/notify-robolectric.init.gradle \
  :android:notifications:testDebugUnitTest :android:notifications:lintDebug
```

The temporary init script sets **test JVM only** `user.home=/tmp/notify-test-home` and
`maven.repo.local=/home/eslam/.m2/repository`; Robolectric otherwise attempts to create a download lock in the
sandbox's read-only home. No repository build settings were changed. Initial setup failures are excluded from the
six behavioral red results.

Final result: **module compilation and all 81 JVM/Robolectric tests pass; lint passes with zero errors and two
warnings** (`InlinedApi`, `AppBundleLocaleChanges`, both existing). `git diff --check` passes.
The referenced `tool/format-changed.sh` does not exist in this checkout or its HEAD; introduced indentation was
corrected only in touched files. No emulator/device run, APK qualification, release or push was performed.

## Consumer contract and delivery boundary

The module was not wired into the app graph at the base, and still has no production ReplyRunSource/ReplyStopper
implementation in this checkout. Wiring must inject one shared `StatusOwners` for all four owners; supply stable
turn identities and the set of watched runs; retain unhandled alert moments until acknowledgement; and route
ReplyStopper by ConversationRef. These are required APIs, without permissive default implementations. Existing
internal legacy StatusOwners in `:android:linux` is not that app binding. This work proves the notification module's
behavior through its real renderers and service body, rather than claiming integrated app behavior.

The sandbox makes the shared worktree Git metadata read-only: normal `git add` fails creating its `index.lock`.
The requested small commits (subjects ending `[skip ci]`, both requested trailers) therefore live in the standalone
local Git repository `/tmp/notify-fixes.git`, on `native/fix-notify-2026-11-01`. The worktree files contain the fixes;
its original shared branch reference is unchanged. The incremental bundle `/tmp/notify-fixes.bundle` preserves the
commits and requires base `066a00be` already present in this repository. Outside this sandbox, import it for review:

```bash
git fetch /tmp/notify-fixes.bundle \
  refs/heads/native/fix-notify-2026-11-01:refs/heads/native/notify-fixes-import-2026-11-01
```

This creates a separate review branch; it does not overwrite the working files. Nothing was pushed.
