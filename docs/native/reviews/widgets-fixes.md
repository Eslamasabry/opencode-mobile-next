# Widgets second-eye fixes — 2026-11-01

Reviewed current base `066a00be` against the review recovered from local commit `3bb7632e` (the review file is absent
at the worktree's base). All six BLOCKER/MAJOR findings still applied. **None was already fixed by integration.**

| Finding | Disposition and regression |
|---|---|
| 1 — BLOCKER: hidden command tail can still be allowed | **Fixed.** `TwoWidgetsRenderTest`: `widgets-BLOCKER-1 clipped enlarged command never offers Allow`, `widgets-BLOCKER-1 explicit command newlines never offer Allow when clipped`, and `widgets-BLOCKER-1 a short host never offers approval for a vertically clipped ask`. Measure the actual command TextView with its font metrics, padding, scaled text and newlines; then check the final RemoteViews layout for ellipsis, incomplete text and vertical/horizontal clipping. Hide and clear the approval target when visibility cannot be proved. |
| 2 — MAJOR: direct preferences bypass acknowledged storage and deletion | **Fixed.** `WidgetPersistenceTest`: `widgets-MAJOR-2 publication enters the warm store and generic profile sweep`, `widgets-MAJOR-2 refused persistence publishes neither data nor changes nor a redraw`, and `widgets-MAJOR-2 profile cleaner removes embedded snapshot after lane closure`. All adopted widget reads/writes now use the graph's existing `KeyValueStore` and logical keys. Target publication precedes snapshot publication; one shared mutex serializes publications. Claims, styles and follow-up tickets use profile lanes. Registered key shapes participate in the generic sweep; `WidgetProfileCleaner` handles the public snapshot and scheduled follow-ups after profile lane closure. No component creates a second adopted store. |
| 3 — MAJOR: late settlement overwrites a foreground snapshot | **Fixed.** `WidgetSnapshotRaceTest`: `widgets-MAJOR-3 foreground publication wins over a late settlement`, `widgets-MAJOR-3 foreground publication wins over an idle patch`, and `widgets-MAJOR-3 foreground publication wins over a result banner`. Capture the exact original bytes and use `KeyValueStore.compareAndSet`; discard a mismatched patch without retry. Tests publish a newer snapshot between the read and CAS while preserving the action identity. |
| 4 — MAJOR: inline completion loses the cancellation handle | **Fixed.** `WidgetRuntimeLifecycleTest`: `widgets-MAJOR-4 inline completion cancels the returned handle`, `widgets-MAJOR-4 destruction during startup cancels the returned handle`, and `widgets-MAJOR-4 concurrent timeout and completion settle once and cancel once`. Synchronize lifecycle transitions, transfer handle ownership once, and cancel a handle returned after completion/destruction. Coroutine cancellation and bounded runtime waits use the same cleanup path. |
| 5 — MAJOR: definite live rejection retains its claim | **Fixed.** `WidgetActionDispatchTest`: `widgets-MAJOR-5 disconnect before posted dispatch releases the claim`. Release a claim for the live dispatcher's matched, sanitized definite rejection before publishing the result. Existing B-ANDR-107 tests continue to retain claims for unmatched/uncertain results; accepted claims remain retained too. |
| 6 — MAJOR: cold admission is inaccessible across modules | **Fixed.** `testWidgetIngressConsumer` compiles `src/ingressConsumer/kotlin/consumer/WidgetIngressConsumer.kt` independently, with no test friend paths, against the real Android compile artifacts. `WidgetActionIngressTest`: `widgets-MAJOR-6 typed ingress promotes before cold admission and settles its own ticket`, `widgets-MAJOR-6 a refused foreground ticket cannot dispatch or claim`, `widgets-MAJOR-6 wrong intent action never enters typed ingress`, `widgets-MAJOR-6 existing live foreground ownership is never promoted or ended`, and `widgets-MAJOR-6 lease expires once and keeps uncertain claims without extending on another tap`. The common typed request/result/ingress live in `:engine:attention`; public `WidgetActionExtras` and `WidgetActionAdmission` implement the Android side. The service-bound controller is removed. Promotion/teardown use `ForegroundWork`, preserve an existing foreground owner and end only the admission's own ticket. |
| 7 — MINOR: Restoring is rendered as stopped | **Rejected from this fix set on scope grounds.** The finding remains valid and open; the request covers BLOCKER and MAJOR findings. No claim that the three-state Status rendering is fixed. |

The shared-file exception is limited to the new `:engine:attention` common ingress contract and its `MODULE_OWNERS`
registration. No existing core, kit, words, routes, shell, connection or transcript source changed. The implementation
uses INTEGRATE-W1/W2's flat core types, existing storage, writer lanes, profile registry and foreground contract.

## Red-first evidence

Before the corresponding implementation changes:

- The rendering/dispatch/runtime run reproduced six assertion failures: the two hidden-tail cases, two startup handle
  cases, disconnect-before-dispatch, and foreground snapshot overwrite (`/tmp/widgets-red2.log`).
- The warm-store/snapshot run reproduced both stale native storage and foreground overwrite
  (`/tmp/widgets-red-storage.log`). The warm-store assertion observed no newly written target through the already-warm
  native store.
- The independent consumer source failed to compile against the base artifacts: `WidgetActionAdmission`,
  `WidgetActionExtras` and the common ingress contract were unresolved (`/tmp/widgets-consumer-red.log`). The same
  source is retained as the Gradle consumer regression, using the pinned Kotlin compiler.

New race coverage additionally checks idle/result CAS, refusal compensation and notifications, profile closure,
embedded cleanup, foreground refusal and ownership. Test fixture setup uses the real acknowledged store. Two inherited
source-path checks were adjusted for native resources/the public ingress; the inherited palette source guard now
ignores whitespace/trailing commas introduced by formatting while preserving its three assertions.

## Validation

The final command is run from `native/`, one Gradle build at a time under
`/home/eslam/Storage/tmp/oc-build.lock`:

```sh
JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 \
ANDROID_HOME=/home/eslam/Android/Sdk \
./gradlew --no-daemon -Pkotlin.incremental=false --no-configuration-cache \
  '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8' \
  --max-workers=2 -I /tmp/widgets-test-env.gradle \
  :android:widgets:testDebugUnitTest :android:widgets:testWidgetIngressConsumer \
  :engine:attention:compileKotlinDesktop :engine:attention:desktopTest
```

The temporary init script sets `robolectric.offline=true` and points `robolectric.dependency.dir` at the existing
Android 35 instrumented jar in the local Maven cache. Without this, Robolectric tried to create a download lock in the
sandbox's read-only home before any tests executed. No repository build settings were changed for this workaround.

**BUILD SUCCESSFUL** (`/tmp/widgets-final3.log`): **172 widget tests, zero failures, zero errors, zero skipped**.
The separate consumer compilation passed. `:engine:attention` compiled for Android (as a widget dependency) and
for desktop; its `desktopTest` task is `NO-SOURCE` because this addition contains only the shared ingress contract.
The Android tests exercise the ingress implementation. Existing warnings remain for the live dispatcher's
unnecessary safe call and the test's deprecated Robolectric `isActivityIntent`; no new warning remains in touched
production code.

Changed Kotlin files were formatted with the pinned ktfmt 0.64 jar (verified SHA-256 from plan 03); `git diff --check`
passes. No APK, emulator/device, launcher, live server, release or deployment qualification was attempted. App graph
and service-shell wiring remain outside this unit; README documents the bindings required by integration.

## Commits and sandbox boundary

The requested shared-worktree commit failed because its Git metadata lives outside the writable sandbox:
`/home/eslam/Storage/Code/cloud-launch/.git/worktrees/codexfix-widgets/index.lock` is read-only. The source changes and
this report remain in the requested worktree. The requested small commits are instead preserved on
`native/widgets-reviewed-fixes` in `/tmp/widgets-fixes-local`, rooted at `066a00be`, and exported to
`/tmp/widgets-fixes.bundle`. Every subject ends in `[skip ci]` and every commit carries both requested trailers.
The committed final files are checked byte-for-byte against this worktree. The shared branch/ref is unchanged.
**Nothing was pushed.**
