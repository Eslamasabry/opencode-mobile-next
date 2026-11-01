# Unit WIDGETS: the RemoteViews widgets in `:android:widgets`

Date: 2026-11-01. Branch: `native/widgets-2026-11-01`, created from `claude/native-widgets-port-9eca18` at `2c8b8b00`.
Write set: `native/android/widgets/**` (new module) and this report. Inputs: `native/AGENTS.md`, plan 01, plan 15 row
S4-WIDGETS, plan 11 §3, §4.2–§4.4, §11 (and §2.4), the S1-LINUX and INTEGRATE-W1 reports, the Flutter widget Kotlin
in `android/app/src/main/kotlin`, catalog rows of `scope.jsonl` with `module: ":android:widgets"`.

**Shipping state:** implemented and verified on this container (module compile plus JVM/Robolectric tests). Committed
and pushed to the branch. Not wired into `:androidApp` (outside the write set). No emulator run, no launcher check, no
PR, no CI, no release.

**Finish line (this brief):** "Port the existing widget providers and binders unchanged where possible, keep the
provider class names frozen, feed them from the native engines; JVM tests."
- **Met for the port and the frozen names.** 35 widget files are copied in the original package. 17 of them are
  byte-identical. The other 18 differ only by the seam edits listed in §3: added import lines, plus class literals
  turned into frozen-FQCN `setClassName` calls. The seven providers and `WidgetActionReceiver` are declared with their
  full frozen FQCNs. A test checks the names against the manifest.
- **Met for the JVM tests.** The 12 widget test classes from plan 11 §4.3 pass (135 tests). The 3 new row-named
  classes also pass (19 tests).
- **Partly met for "fed from the native engines".** The Status widget's Linux line reads `:engine:runtime`'s
  `RuntimeStatus` (seam E10). The live-connection state, the dispatcher and the background runner are ports
  (`WidgetFeed`) that the app graph binds. The snapshot producer (`WidgetSnapshotBuilder`) and
  `WidgetActionRunner`/`WidgetStatusRunner` belong to `:engine:attention` (S4-ATTNE), which does not exist yet. Until
  they are bound, a widget tap reads "unavailable" and a background run settles as uncertain/unavailable, never as a
  retryable rejection.

**Non-goals (kept):** Glance and new sizes (NG13), the overlay panels and the activity shells (`:androidApp`,
S4-PANELS), the `:engine:attention` snapshot builder and runners, E2E-26, and the launcher photos (A11-8).

---

## 1. What was built

| Path (under `native/android/widgets/`) | What it holds |
|---|---|
| `build.gradle.kts` | `oc.android.library`. Robolectric reads the resources (`isIncludeAndroidResources`). Depends on `:engine:runtime`, `androidx.core:core-ktx` and `work-runtime` (catalog fallback, §5); tests use junit4, Robolectric and `org.json`. |
| `src/main/AndroidManifest.xml` | The seven provider receivers and `WidgetActionReceiver`, copied from the Flutter manifest with the FQCN written out, plus `supportsRtl="true"` (as on the Flutter `<application>`). |
| `src/main/kotlin/io/github/eslamasabry/opencode_mobile/` (35 copies) | `SessionsWidgetProvider` and its `Wide`/`List`/`Strip` subclasses, `StatusWidgetProvider`, `UsageWidgetProvider` and `UsageWideWidgetProvider`, `WidgetProviders`, the binders and renderers (`ConversationsWidgetBinder`, `SessionWidget*`, `UsageWidget*`), the policies (`WidgetV2Policy`, `WidgetActionPolicy`, `WidgetStatusPolicy`, `WidgetLayoutPolicy`/`Rules`, `UsageWidgetFit`/`Plans`), `WidgetGraphite`, `WidgetV2Style`, `WidgetCopy`, `StatusWidgetModel`, and the action path (`WidgetV2Bridge` with `WidgetActionReceiver`, `WidgetActionBridge`, `WidgetActionLease`, `WidgetActionSnapshot`, `BackgroundWidgetActionController`, `WidgetStatus{Identity,Scheduler,Tickets,Worker}`). |
| `src/main/kotlin/io/github/eslamasabry/oc/android/widgets/` (native-authored) | `FrozenComponents.kt`: seam objects with the frozen names and constants. `NativeStrings.kt`: the Flutter file with only the package line changed. `WidgetActionRuntime.kt`: same constructor as the Flutter class, but it runs through the bound runner. `WidgetFeed.kt`: `PhoneServerLine`, `WidgetLiveDispatcher`, `WidgetBackgroundRunner`, and `WidgetFeed`'s `onRuntimeStatus`/`onConnection`/`onWidgetActionPending`. `LiveWidgetDispatch.kt`: the Flutter `dispatchWidgetAction` body without the MethodChannel. `AndroidWidgetSurface.kt`: the Android half of plan 11 §11.3's `WidgetSurface` (`publish`, `redrawAll`, `invalidateConnection`, `clearProfile`). |
| `src/main/res/` | Verbatim copies of the 11 widget layouts, the 7 provider XMLs, the widget drawables, the 14 `previewImage` PNGs (`drawable-nodpi` and `drawable-night-nodpi`), `colors.xml` (light and night) and `widget_v2_strings.xml` (en and ar). `strings.xml` (en and ar, 39 entries) and `styles.xml` (11 `Widget*` styles) are verbatim subsets. `widget_seams.xml` aliases `mipmap/ic_launcher`; the app's real icon wins at merge. |
| `src/test/kotlin/io/github/eslamasabry/opencode_mobile/` (12 copies) | `StatusWidgetModelTest`, `TwoWidgetsRenderTest`, `UsageWidgetFitTest`, `UsageWidgetPlansTest`, `WidgetActionLeaseTest`, `WidgetActionPolicyTest`, `WidgetCardRenderTest`, `WidgetConversationPresentationTest`, `WidgetProvidersTest`, `WidgetSessionCardLayoutTest`, `WidgetStatusPolicyTest`, `WidgetV2PolicyTest`. |
| `src/test/kotlin/io/github/eslamasabry/oc/android/widgets/` (native, row-named) | `WidgetSurfaceTest` (5 tests), `WidgetActionDispatchTest` (8), `WidgetFeedTest` (6). |
| `README.md` | Layout, the seam table, how the engines feed the widgets, and the check command. |
| `TWINS-rows.md` | 111 rows for `native/TWINS.md`: native path, source path, last source commit, owner, and same/seam. |

## 2. Catalog rows covered

Tests were written first and committed first (`00fbc66d`, before the sources in `65598e63`). They are named after
their rows:

| Row | Test | What it proves here (the Android half; the Dart halves port to `:engine:attention`) |
|---|---|---|
| B-ANDR-022 | `WidgetSurfaceTest` › the snapshot is written, the target first, and every widget redraws | `oc.widgetTarget.<p>` is committed before `oc.widgetSessions`, and one redraw follows. |
| B-ANDR-117 | `WidgetSurfaceTest` › unchanged widget payloads are neither rewritten nor redrawn; a publish with no profile writes nothing | No prefs write and no redraw for identical bytes. A changed byte writes. |
| B-ANDR-121 | `WidgetActionDispatchTest` › malformed actions never enter background dispatch; the same action is dispatched once and its result lands in the snapshot; an accepted action leaves no banner; a stale profile scope never reaches the conversation | Over the copied bridge and policy plus the native dispatch seam. |
| B-ANDR-107 (amended) | `WidgetActionDispatchTest` › an answer for another action or an unknown status is uncertain, never a retryable reject; a raw server error never crosses into the snapshot; no answer within the dispatch budget reads uncertain | The claim stays after an uncertain delivery, so a second tap reads `busy`. The 6.5 s budget gives `uncertainDelivery`. |

The other tests are named after plan clauses, because no catalog row covers them:

- `P11-4_4 E10` ×2: the Linux line follows `RuntimeStatus`, and the widget redraws only on a change.
- `P11-11_3` ×4: invalidating the connection, clearing a profile, connection start/end, and no live connection.
- `P11-3` ×2: frozen provider names (in the code and the manifest), and taps target the frozen components.
- `P11-4_2` ×1: with no runner bound, a run settles once with nothing; `destroy` drops a late answer.

The 12 copied classes carry no row ids; renaming them would edit copies (same as S1-LINUX P4). Rows B-ANDR-010,
100, 103–106, 111–116, 118–120, B-SEC-180/183 and B-AGENT-317 are listed for `:android:widgets` in `scope.jsonl`.
Their sources are Dart suites (`widget_snapshot_test`, `widget_action_runner_test`, …) whose subjects are the snapshot
builder and the runners. Plan 11 §11.3 puts those in `:engine:attention`, so the rows stay open for S4-ATTNE. See P2.

## 3. Seams (the only edits to copied files)

Every edit is one of these. README.md has the full table.

- **W-R:** `import io.github.eslamasabry.oc.android.widgets.R`. A shim `R` in the app package would duplicate
  `:androidApp`'s own `io.github.eslamasabry.opencode_mobile.R` in the dex.
- **E4:** `import …widgets.NativeStrings`. This is a package-moved copy, because `:android:linux` already holds one in
  the app package.
- **E5/E7:** import the seam objects `MainActivity`, `BackgroundConnectionService`, `StatusNotification`,
  `WidgetOverlayActivity` and `WidgetRouteActivity`. Five class literals became `Intent().setClassName(ctx,
  X.COMPONENT)`, which builds the same component.
- **E10:** import `BuiltinLinux`/`BuiltinServerService`; both read `WidgetFeed.phoneServer`.
- **W-RT:** import the seam `WidgetActionRuntime`.

Each import resolves to the module's own package (`io.github.eslamasabry.oc.android.widgets`), so none of the seam
classes clashes with the FQCN of a shell in `:androidApp` or a class in `:android:linux`. Function bodies of the
copies are unchanged.

There are four test edits:
- R imports in `TwoWidgetsRenderTest` and `WidgetCardRenderTest` (W-R).
- In `WidgetProvidersTest`, `removePrefix(".")` became `substringAfterLast(".")`, because the library manifest writes
  FQCNs.
- `WidgetActionLeaseTest` drops its two lines that read `BackgroundConnectionService.kt`, which moves with S4-BG.

## 4. Commands and results

From `native/`, with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `ANDROID_HOME=/opt/android-sdk`.
cmdline-tools came from `commandlinetools-linux-16111833_latest.zip`, with `platform-tools`, `platforms;android-37.1`
and `build-tools;37.0.0` installed. AGP also pulled `build-tools;36.0.0`. `~/.gradle/gradle.properties` was set to
`-Xmx6g`, 4 workers.

| Command | Result |
|---|---|
| `./gradlew :android:widgets:compileDebugUnitTestKotlin --max-workers=1 --no-configuration-cache` | BUILD SUCCESSFUL. One warning: the deprecated Robolectric `isActivityIntent` in `WidgetFeedTest`. |
| `./gradlew :android:widgets:testDebugUnitTest --max-workers=1 --no-configuration-cache` | BUILD SUCCESSFUL. 15 classes, **154 tests, 0 failed, 0 skipped** (copied 135, native 19). |

The first test run had 4 failures, now fixed:
- The Arabic render tests failed until `supportsRtl` was added to the library manifest.
- My dispatch test expected the wrong snapshot banner; the latest result wins.
- My surface test needed WorkManager initialized.

No copied test was changed to make it pass.

**Network in this container.** Maven Central answered 429 for many jars. A stale daemon and the configuration cache
then replayed the failure in about 1 s; `--stop` plus `--no-configuration-cache` cleared it. Two large jars only came
through from Google's Maven Central mirror (SHA-1 matched Central's):
- Gradle's `nativeruntime-dist-compat-1.0.19` came through a machine-local init script,
  `~/.gradle/init.d/central-mirror.gradle.kts`.
- Robolectric's `android-all-instrumented-15-robolectric-13954326-i7` was pre-seeded into `~/.m2/repository`.

Neither change is in the repository.

Not run (outside "build and test only your own modules"): `:quality:test` (see P1), `build-logic test` and
`:androidApp`. Android lint was not run.

## 5. Deviations

- **D1. Provider classes live in the library, not as `:androidApp` shells.** Plan 11 §3 puts "shell only" in
  `:androidApp`, with the bodies in `:android:widgets`. `:androidApp` is outside the write set, so the copied
  providers are declared in the library manifest with their frozen FQCNs. The manifest merger puts them into the app
  unchanged. When the coordinator moves the shells, they move as a pair: `WidgetProvidersTest` and the `P11-3` test
  read this manifest.
- **D2. The overlay and route activities are not copied:** `WidgetOverlayActivity`, `WidgetRouteActivity` and
  `WidgetConfigureActivity`, along with `WidgetOverlaySession` and `WidgetOverlayStyle`, which import Flutter. They are
  `:androidApp` shells and `:feature:widget-panels` (S4-PANELS, W11-11). The copies only need their frozen names and
  `READY_META`. `WIDGET_OVERLAY_READY` is not declared here, so `WidgetV2Bridge.overlayReady` reads false until the app
  declares it. Steer, voice and Edit then fall back the way the Flutter app does before the overlay shipped.
- **D3. `WidgetActionRuntime` (the headless Dart engine) is replaced, not copied**, as plan 11 §4.2 says ("delete").
  The seam class keeps its constructor, so `BackgroundWidgetActionController` and `WidgetStatusWorker` stay unchanged.
- **D4. `WidgetStatusWorker` is still a `Worker`, not a `CoroutineWorker`.** Plan 11 §3 allows the switch, but the
  copy stays unchanged until `WidgetStatusRunner` exists.
- **D5. `AndroidWidgetSurface` returns plain values** (`PublishResult`, `ClearOutcome`), not `Outcome`, and is
  synchronous. The `WidgetSurface` interface belongs to `:engine:attention` (CR11-7), and S4-ATTNE adapts this class to
  it. Callers run it on their own I/O lane; there is no dispatcher in this module (rule 6).
- **D6. Prefs stay in the Flutter file with the `flutter.` prefix** (`FlutterSharedPreferences`,
  `flutter.oc.widgetSessions`, …), because the copied renderers read those keys. That matches the in-place adoption
  of plan 05. The rename, if any, is the migration owner's call.
- **D7. `BuiltinServerService.isForegroundRunning` reads false.** Its meaning, "the server service is in the
  foreground", is folded into `PhoneServerLine.Running` from `RuntimeStatus.serverRunning` (E10).

## 6. Catalog requests

- `androidx-work-runtime = { module = "androidx.work:work-runtime", version.ref = "work" }`. The version 2.12.0 is
  already in `[versions]`. The build file uses `catalogOr("androidx-work-runtime", …)` until then.
- `json = { module = "org.json:json", version = "20180813" }`. This is the same request as S1-LINUX.
- `MODULE_OWNERS`: a row `:android:widgets	F	widgets (S4-WIDGETS)	slice-4`. Without it, `ModuleOwnersTest` in
  `:quality` is red.
- `native/TWINS.md`: append `native/android/widgets/TWINS-rows.md`.

## 7. Open problems

- **P1.** `:quality` `ModuleOwnersTest` fails until `MODULE_OWNERS` lists the module (§6). It was not run here.
- **P2.** The rows owned by the Dart snapshot builder and runners (§2) need `:engine:attention` (S4-ATTNE). Plan 15 is
  ambiguous: `scope.jsonl` assigns them to `:android:widgets`, but plan 11 §11.3 puts the code in `:engine:attention`.
  The coordinator should decide whether the rows move module.
- **P3. App wiring:**
  - `:androidApp` has to depend on `:android:widgets`.
  - Its graph has to bind `WidgetFeed.liveDispatcher`/`backgroundRunner`.
  - It has to forward `RuntimeStatus` and connection state through `WidgetFeed.on…`.
  - It has to call `AndroidWidgetSurface.redrawAll(ForceStopped/Boot/PackageReplaced)` from the restore path
    (S4-RESTORE).
  - It has to declare the activity shells and `WIDGET_OVERLAY_READY`.
- **P4. Duplicate `R` in the dex.** `:android:linux`'s shim `io.github.eslamasabry.opencode_mobile.R` has the same FQCN
  as `:androidApp`'s generated `R`. That gives a duplicate class once both are in the APK. This module avoids the
  problem with W-R imports; `:android:linux` should do the same (S3-RTSPLIT).
- **P5. Not done here:**
  - `WidgetBitmapBudgetTest` and `ConversationsWidgetPlanTest` (plan 11 §11.6, new tests).
  - Regenerating the preview PNGs. They are unchanged copies; the render tests' `pickerPreviews` writes to `build/`.
  - E2E-26 and the owner's launcher photos (A11-8).
- **P6.** The copied tests do not carry row ids (§2). Mapping them to rows is the test-writer lane's job.
