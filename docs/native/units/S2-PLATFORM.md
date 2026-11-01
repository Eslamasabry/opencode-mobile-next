# Unit S2-PLATFORM: deep links, share in and out, Tailscale, static shortcuts and ActivityResults in `:android:platform`

Date: 2026-11-01. Branch: `native/platform2-2026-11-01`, cut from `claude/native-platform-deep-links-yqifjg` at
`c5654cf7` (= `native/m0-2026-11-01`), with `native/entry-2026-11-01` (S2-ENTRY, `809d6fd9`) merged in first (D1).
Inputs: `native/AGENTS.md`, `docs/native/slice0/*`, `docs/native/units/*` (S1-PLAT0 and S2-ENTRY above all), plan 01,
plan 15 row S2-PLATFORM, plan 07 §7 (§7.1, §7.4, §7.6, §7.7, §7.11, §7.13), plan 11 §2.3, §3, §14, §16, plan 16
§1.8 (untrusted arrivals only navigate), plan 10 §10.3–§10.4, plan 12 R-08/N-51, map `android-platform.md` 29–34, the
Flutter-era Kotlin (`LaunchShareChannel.kt`, `LaunchLinkChannel.kt`, `LaunchShortcutChannel.kt`, `TailscaleHandoff.kt`,
`res/xml/shortcuts*.xml`) at `bd0a13d6`.

**Shipping state:** implemented and verified in this container (compile, Android lint, JVM + Robolectric tests,
planted-fault red checks). Committed and pushed. **Not wired** into `:androidApp` (coordinator-owned: `MainActivity`,
the manifest and the app graph), no emulator run, no PR, no CI, no release.

**Finish line (plan 15):** "Deep links, text share in and out, the Tailscale handoff and static shortcuts reach
`EntryRouter`; `ActivityResults` is registered on `MainActivity`."
- **Reach `EntryRouter`: met at module level.** `EntryCapture` is the whole 07 §7.1 capture; Robolectric tests drive a
  real activity through it into S2-ENTRY's real `DefaultEntryRouter` and assert the handoff for share, session and team
  links, both static shortcuts, the tile long-press and every launch value. Share out and the Tailscale handoff are
  services (`ShareService`, `TailscaleApp`), not entries, and are implemented and tested.
- **Registered on `MainActivity`: ready, one call away.** `AndroidActivityResults.attach(activity)` registers the six
  fixed-key launchers; `MainActivity` is `:androidApp` (not in this write set), so the call is open problem P1. The
  exact edit is below.

**Non-goal (kept):** widgets, tiles, pinned shortcuts (S4); images and Direct Share in (O-07-4); `ExternalOpen` /
`openLocalFile` and `ContentAccess` (later media units).

---

## 1. What was built

All in `native/android/platform/**` (package `io.github.eslamasabry.oc.android.platform`), plus this report.
`EntryIntentReader.kt` (S2-ENTRY) and the S1-PLAT0 files are untouched.

| Path | What |
|---|---|
| `build.gradle.kts` | adds `api(:engine:entry)` (S2-ENTRY's catalog request), `libs.androidx.activity.compose` (plan 12 N-51), `isIncludeAndroidResources` for the resource tests |
| `src/main/AndroidManifest.xml` | adds `<queries><package com.tailscale.ipn/></queries>` (16 §1.8.2); still no components (11 §2.3) |
| `EntryCapture.kt` | 07 §7.1 for `MainActivity` / `.InternalEntryActivity`: `onCreate(activity, savedInstanceState, origin, clock, router)` skips restored and from-history activities; `onNewIntent(...)`; both do `EntryIntentReader.read` → `strip` → `activity.intent = …` → `router.submit` each request; return the count |
| `StaticShortcuts.kt` | the frozen contract: ids `connect`, `new_task`, extra `oc.shortcut`, `MainActivity` FQCN, stable and preview packages, placeholder `appShortcuts` and `resourceFor(applicationId)` |
| `res/xml/shortcuts.xml`, `res/xml/shortcuts_native.xml` | copied from Flutter `bd0a13d6`; the preview copy targets `io.github.eslamasabry.opencode_mobile.native` (11 §2.3); only the comment changed |
| `res/values{,-ar}/strings_shortcuts.xml`, `res/drawable/ic_shortcut_{connect,new_task}.xml` | the four labels (names and en/ar text verbatim) and the two icons, copied |
| `AndroidShareService.kt` | `ShareService` (`:core:domain`): `shareText` = chooser with `EXTRA_EXCLUDE_COMPONENTS = [<pkg>/…MainActivity]`; `shareFile` only for `content://<pkg>.localfiles/…` with `FLAG_GRANT_READ_URI_PERMISSION` + `ClipData` on the target **and** the chooser; empty text, foreign URI → `Failure.Unexpected`; any start refusal → `Failure.Device`. Also `CurrentActivity` and `startFromVisibleActivity` (visible activity, else application + `NEW_TASK`) |
| `AndroidTailscaleApp.kt` | `TailscaleAppState`, `TailscaleApp` (D3) and the port of `TailscaleHandoff`: `Installed` only for found **and** enabled; `NameNotFound` → `Missing`; anything else → `Unavailable`; `open()` starts an explicit MAIN/LAUNCHER intent only when the launch intent names `com.tailscale.ipn` |
| `AndroidActivityResults.kt` | `ActivityResults` (`:core:domain`) + `CurrentActivity`: `attach(ComponentActivity)` registers `oc.pickPhoto`, `oc.pickPhotos`, `oc.takePhoto`, `oc.openDocument`, `oc.createDocument`, `oc.openDocumentTree` on the activity's registry; one waiter per key (a second request fails without launching); results with nobody waiting → `LauncherOutcome.Captured / Cancelled / Picked(files)` to the `onOrphanPhoto` sink (photo keys only); `PickImages` (room per call, never more than the room returned) and `CreateDocument` (name and MIME per call) contracts; `takePhoto` only into a `content://` target; metadata from `OpenableColumns` and `getType` (errors leave hints null); the attachment is dropped on `ON_DESTROY` |
| tests (`src/test/…`) | `EntryTestRig.kt` (fakes for the real router), `EntryCaptureTest` (10), `StaticShortcutsTest` (5, + fixture `resources/entry/main-activity.xml`), `AndroidShareServiceTest` (5), `AndroidTailscaleAppTest` (5), `AndroidActivityResultsTest` (9) |

### Wiring the coordinator needs (`:androidApp`, open problem P1)

```kotlin
// MainActivity (07 §7.1; full configChanges + taskAffinity="" stay in the manifest)
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val graph = appGraph
    graph.activityResults.attach(this)                     // before onStart: the registry refuses later registrations
    EntryCapture.onCreate(this, savedInstanceState, EntryOrigin.Untrusted, graph.clock, graph.entryRouter)
    setContent { … }
}
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    EntryCapture.onNewIntent(this, intent, EntryOrigin.Untrusted, appGraph.clock, appGraph.entryRouter)
}
// graph: @Provides AndroidActivityResults(dispatchers, onOrphanPhoto = { /* PendingPhotoRecovery later; no-op now */ })
//        as ActivityResults and as CurrentActivity; AndroidShareService(app, it) as ShareService;
//        AndroidTailscaleApp(app, it) as TailscaleApp.
```

Manifest: paste the filters and meta-data of `src/test/resources/entry/main-activity.xml` into `.MainActivity` and set
`manifestPlaceholders["appShortcuts"] = StaticShortcuts.resourceFor(applicationId)` (the test checks that fixture).

## 2. Catalog rows covered

Test names start with the row id (AGENTS rule 11). Rows S2-ENTRY already proves in common code are proved here only for
the Android half (a real `Intent` on a real activity into the real router).

| Row | Tests | What this module proves |
|---|---|---|
| B-ANDR-029 | `StaticShortcutsTest` ×4, `EntryCaptureTest` ×1 | the shipped XML declares exactly `connect`, `new_task`, each an explicit MAIN for `MainActivity` with `oc.shortcut = id`; the values parse through `EntryIntentParser` as **untrusted** `Launch`; preview differs only in package; labels en/ar and icons exist; on a real activity the extra is removed, `connect` → `Servers(null)`, `new_task` → `NewConversation` with **zero** connect/create; an unknown value submits nothing and is still stripped |
| B-ANDR-072 | `StaticShortcutsTest` | the proposed `MainActivity` element has exactly one VIEW filter (DEFAULT + BROWSABLE, `opencode-mobile` + hosts `session`, `team`), exactly the four filters MAIN / SEND `text/plain` / VIEW / QS_TILE_PREFERENCES, `singleTop`, `taskAffinity=""`, and `android.app.shortcuts` = `${appShortcuts}`. Proves the fixture, not yet the real manifest (P1) |
| B-ANDR-074 | `EntryCaptureTest` ×3, `AndroidShareServiceTest` ×1 | a share reaches the router once and becomes `CreateSession` → `SeedDraft(subject\nbody)` → `Chat(focusComposer, NewlyCreated)`, nothing sent; the intent is cleaned (MAIN, no extras, no type); rotation (`savedInstanceState`) and Recents (`FROM_HISTORY`) never replay; an empty share submits nothing; a live share via `onNewIntent` replaces the activity intent; share out excludes this app |
| B-ANDR-077 | `AndroidShareServiceTest` ×3 | text and subject go to the chooser and the call reports `Ok`; no subject → none sent; `ActivityNotFoundException` → `Failure.Device`, empty text → failure, nothing thrown; no visible activity → application + `NEW_TASK` |
| B-SEC-174 (native half) | `AndroidTailscaleAppTest` ×3 | missing → `Missing`; installed and enabled → `Installed`; disabled → `Unavailable`; a throwing package manager → `Unavailable` and `open()` false. The Dart half (HTTPS origins, MagicDNS names) is `:core:security`'s |
| B-ANDR-028 | `EntryCaptureTest` ×2 | **`UntrustedArrival`** (07 §7.4): all 12 launch values (incl. three `usage_target` forms), a pinned session and an alert for another server, a scheduled tap and a team link fired at `MainActivity` → zero `connect` / `createSession`. **`TrustedArrival`**: the same `new_task` with `EntryOrigin.Internal` runs `createSession` |
| B-ANDR-071 (amended) | `EntryCaptureTest` ×2 | a session link reaches the router → `Chat(p1, ses_1)` with no gateway call; another saved server → `SwitchServerThen` (asks first); a 1,100-char link and an unknown host are consumed (data null, MAIN) and submit nothing |
| B-ANDR-073 | `EntryCaptureTest` | the Pause tile long-press (`EXTRA_COMPONENT_NAME`) → `Settings(Background)` |
| B-ANDR-027 | `EntryCaptureTest` | before the gate, a cold-start `connect` then a live `background_settings` through `onNewIntent`: only the newer opens |
| B-MEDIA-007 (Android half) | `AndroidActivityResultsTest` | room ≥ 2 → `PickImages` with `EXTRA_PICK_IMAGES_MAX` = room and never more files than the room back; room 1 → single `PickVisualMedia`; cancel → empty; room 0 → no picker |
| B-MEDIA-008 (Android half) | `AndroidActivityResultsTest` ×3 | a pending request blocks another one (no second launch); the camera writes only to a `content://` target; a camera result after process death (registry state saved and restored, new graph) reaches only the recovery sink as `Captured` |
| B-ANDR-037 | — | listed on the plan 15 row; already covered by S1-PLAT0 (`NetworkTrackerTest`, Robolectric). Nothing new here |

Seven tests are named `S2-PLATFORM …` (no catalog row): launcher registration on attach and release on destroy, no
activity → `Failure.Device`, open-document result and cancel, document create/tree results, a refused launch frees the
slot; plus share-out of a
FileProvider file (grant + ClipData, foreign URIs refused) and Tailscale `open()` (only that package, `NEW_TASK` with no
activity).

## 3. Commands and results

Container only (nothing in the repo): Android command-line tools 16111833 in `/opt/android-sdk/cmdline-tools/latest`,
licences accepted, `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`; JDK 17 + 21 in `/usr/lib/jvm`;
`~/.gradle/gradle.properties` (auto-download off, explicit JDK paths); `native/local.properties` (gitignored);
`BASH_DEFAULT_TIMEOUT_MS` raised. Maven Central is throttled through the proxy, so `~/.gradle/init.d/mirror.gradle.kts`
puts Google's Central mirror first (`beforeSettings` for plugins, `settingsEvaluated` for dependencies) and passes
`robolectric.dependency.repo.url` **to the test JVM** (`tasks.withType<Test>().systemProperty`) — a
`systemProp.` line in `gradle.properties` reaches only the Gradle JVM, and every Robolectric class then fails with
"Failed to fetch maven artifact android-all-instrumented".

From `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`:

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :engine:entry:compileAndroidMain :android:platform:compileDebugKotlin` (after the merge, old build file) | `:engine:entry` OK; `:android:platform` fails on `EntryIntentReader.kt` (unresolved `engine`) — S2-ENTRY's expected request |
| 2 | `./gradlew :android:platform:testDebugUnitTest` (tests written, no implementation) | compile errors only in the new tests (`AndroidActivityResults`, `EntryCapture`, … unresolved): red |
| 3 | same, first implementation | compile error: core's inline `Outcome.map` cannot be inlined (JVM 17 into JVM 11; D5) → local helper |
| 4 | same | 56 tests, 2 failed: the trusted `new_task` focuses the composer (S2-ENTRY's design; test corrected), and a Robolectric re-install after `deletePackage` (test split) |
| 5 | same | **57 tests, 0 failures** (23 S1-PLAT0 + 34 new) |
| 6 | `./gradlew :android:platform:lintDebug` | 2 `UseKtx` warnings (`String.toUri`) → fixed → **0 issues** |
| 7 | planted faults: restored activity replays; capture forced to `Internal`; exclude-components removed; disabled Tailscale = installed; one-owner check removed | **6 tests failed**, one or more per fault (B-ANDR-074 ×2, B-ANDR-028, B-ANDR-029, B-SEC-174, B-MEDIA-008); reverted with `git checkout` |
| 8 | `./gradlew :android:platform:testDebugUnitTest :android:platform:lintDebug` (final, after the revert) | **57/57 passed; lint 0 issues** |
| 9 | grep of the branch diff under `native/android/platform` for RFC 1918 / CGNAT addresses and e-mail addresses | no match |

Not run: `:quality:test`, `:androidApp:*` and other modules (brief: they may not compile during the core
reconciliation; S2-ENTRY found `:engine:connection` broken), no emulator, no formatter (`tool/format-changed.sh` does
not exist yet; files follow the existing style).

## 4. Deviations

- **D1 — S2-ENTRY merged into this branch.** `:engine:entry` is not on `native/m0` yet and `EntryIntentReader.kt` (in
  this module) needs it to compile, so `native/entry-2026-11-01` is merged as the first commit (no conflicts, no edits
  to its files). Merge S2-ENTRY first; this branch then fast-forwards over it.
- **D2 — Static shortcut XML, labels and icons ship from `:android:platform`**, not `:androidApp` (11 §2.3 puts them
  in the app). The brief puts "static-shortcut files of `native/android/platform/**`" in this write set and the app is
  coordinator-owned; library resources merge into the app unchanged, names and text are verbatim, so moving them to
  `:androidApp` / `strings_os` later is a file move. The manifest part (filters, meta-data, placeholder) stays in
  `:androidApp` and is delivered as a checked fixture.
- **D3 — `TailscaleApp` / `TailscaleAppState` live here** with `TODO(move-to-core)` (11 §14.6 / CR11-9 put them in
  `:core:domain`, which lacks them; core may not be edited by this unit). Platform-free; they move unchanged.
- **D4 — `ActivityResults` follows the frozen `:core:domain` types**, not plan 10's sketch: `PickedFile(uri, …)`,
  `PhotoTarget(uri)`, `SharedFile(uri, mime, displayName)`. `LauncherOutcome` (plan 10 §10.4) is defined here because
  core has no recovery type yet; the recovery sink is a constructor lambda (no-op until `PendingPhotoRecovery`).
  "One owner per result" and "no activity" are `Failure.Device` (core has no Busy failure).
- **D5 — no core inline functions from Android libraries.** `oc.android.library` compiles Kotlin for **JVM 11** while
  `oc.kmp.library` sets **JVM 17**, so `Outcome.map`, `attempt`, etc. fail with "Cannot inline bytecode built with JVM
  target 17 into … 11". A private non-inline `mapOk` is used. Build-logic owner: set `jvmTarget = 17` in
  `OcAndroidLibraryPlugin` too (catalog request below), or `attempt {}` (AGENTS rule 5) is unusable in every Android
  module.
- **D6 — `EntryCapture` takes the router and clock as arguments** rather than reading `application as GraphOwner`, so
  the module needs no app types and the test drives the real router. `onCreate` checks `savedInstanceState` and
  `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` itself (07 §7.1).
- **D7 — share out returns `Failure.Unexpected` for empty text or a URI that is not ours**; 11 §14.2 names only the
  `ActivityNotFoundException` → `Failure.Device` case. A file is shared only as `content://<pkg>.localfiles/…`
  (stricter than "any FileProvider URI").

## 5. Catalog requests

- **Build-logic:** `OcAndroidLibraryPlugin` (and the application plugin) `kotlin { compilerOptions { jvmTarget = JVM_17 } }`
  to match the KMP modules (D5).
- **Coordinator:** `MODULE_OWNERS` row for `:android:platform` (S1-PLAT0 P2) and `:engine:entry` (S2-ENTRY).
- No new libraries. `androidx.activity:activity` alone would do instead of `activity-compose` if the registry wants
  `:android:platform` free of Compose (`androidx-activity = { module = "androidx.activity:activity", version.ref = "activity" }`).

## 6. Open problems

- **P1 — wiring (`:androidApp`, coordinator).** The `MainActivity` and graph edit of §1, the manifest filters and
  meta-data from `src/test/resources/entry/main-activity.xml`, `taskAffinity=""` and the full `configChanges` list
  (missing from today's native manifest), the `appShortcuts` placeholder, and the new non-exported
  `.InternalEntryActivity` calling `EntryCapture` with `EntryOrigin.Internal`. Until then nothing reaches the router in
  the app, and B-ANDR-072 checks the fixture, not the real manifest: point `StaticShortcutsTest` (or `:quality`'s text
  check, 07 §7.13) at `native/androidApp/src/main/AndroidManifest.xml` once the filters are pasted.
- **P2 — `quality/freeze/entry-extras.txt`** (07 §7.13) still does not exist; the `EntryContractTest` that reads it
  belongs here once it does (S2-ENTRY P2).
- **P3 — untrusted share creates an empty session** (S2-ENTRY open problem 3): kept as 07 §7.7 says; `UntrustedArrival`
  covers launcher actions only.
- **P4 — `ExternalOpen` / `ContentAccess` / `openLocalFile`** (10, 11 §14.3) and the camera FileProvider root (C10-14)
  are not built; `takePhoto` checks only that the target is a `content://` URI.
- **P5 — not proven on a device.** Robolectric only. The emulator journey (E2E-25 entry parts: launcher shortcut, share
  from another app, `adb shell am start -d opencode-mobile://session?...`, Tailscale open) waits for P1 and a T3 run.
- **P6 — `native/TWINS.md` rows** for `LaunchShareChannel.kt` (share out), `TailscaleHandoff.kt` and
  `res/xml/shortcuts*.xml` (source `bd0a13d6`) are not added: the file is outside this write set.
