VERIFIED: green

# P12V PREVIEW-QR — cloud verification of unit P12 (dev preview + QR/link pairing)

Date: 2026-11-02. Verified branch: `claude/verify-preview-qr-parity-jjv6x9` (the unit's commits on top of base
`native/m1-2026-11-03` at `8a4eee69`). Unit report: [P12-PREVIEW-QR.md](P12-PREVIEW-QR.md).

Scope of "green": every module the unit touched compiles and its tests pass, `:androidApp:assemblePreviewDebug`
builds, and `:quality:test` passes except one K1 failure in a **base** file the unit never touched (§3). The base
snapshot itself does not compile `:feature:chat` test sources (§3); that is the base's composer WIP, not this unit.

## Environment

- `dl.google.com` reachable (HTTP 200 for `repository2-3.xml`).
- Android SDK at `/opt/android-sdk` from `commandlinetools-linux-16111833_latest.zip`: `platform-tools`,
  `platforms;android-37.1`, `build-tools;37.0.0` (as named in `native/AGENTS.md` and `build-logic/.../Sdk.kt`).
  `native/local.properties` (`sdk.dir`, gitignored). Gradle 9.7.1 on OpenJDK 21.
- Maven Central (`repo.maven.apache.org`) answered **HTTP 429** to this container's parallel resolution, which also
  poisoned plugin resolution ("plugin was not found"). Fixed **outside the repo** with a user-level init script
  `~/.gradle/init.d/central-mirror.gradle.kts` that points Maven Central at Google's mirror
  (`maven-central.storage-download.googleapis.com/maven2/`). Nothing in the repo was changed for this.

## 1. Commands and results

All from `native/` with `ANDROID_HOME=/opt/android-sdk JAVA_HOME=<jdk21>`.

| Command | Result |
|---|---|
| `./gradlew :feature:preview:desktopTest :feature:servers:desktopTest :feature:chat:desktopTest --continue` (first run, unit as pushed) | main sources of all three compile. **3 failures**: preview 29 tests / 1 failed, servers 41 / 2 failed; `:feature:chat:compileTestKotlinDesktop` fails (base, §3) |
| after fixes: `:feature:preview:desktopTest :feature:servers:desktopTest :ui:routes:desktopTest :ui:words:desktopTest :android:webview:testDebugUnitTest :android:camera:testDebugUnitTest :android:camera:compileReleaseKotlin :feature:chat:compileKotlinDesktop :androidApp:assemblePreviewDebug --continue` | **BUILD SUCCESSFUL** (495 tasks). Tests: `:feature:preview` **29/0 failed**, `:feature:servers` **41/0**, `:android:camera` **41/0**, `:ui:words` **4/0**, `:ui:routes` **1/0**; `:android:webview` has no tests (compiles, incl. `KitWebPreview.kt`). No skips. |
| `:androidApp:assemblePreviewDebug` | built `androidApp-preview-debug.apk` (31 MB), package `io.github.eslamasabry.opencode_mobile.native`; `aapt2 dump badging` shows `uses-permission CAMERA` and `uses-feature-not-required android.hardware.camera`. `PreviewQrWiring.kt` compiled with **no changes needed** (Metro bindings, CameraX `SurfaceRequest`, `:android:camera` camerax source set enabled by the catalog aliases). |
| `./gradlew :quality:test` | first: `compileTestKotlin` fails (base `ChatWorldReviewTest`, §3). After the one-line fix: **48 tests, 47 passed, 1 failed** — `K1 features arrange kit parts only`, sole invalid file `feature/chat/.../composer/BasicComposerHost.kt` (base, untouched by the unit). ModuleOwners, graph smoke and every other rule pass, and K1 passes for all of the unit's files. |
| `./gradlew -p build-logic test` (unit edited `ExternalRules.kt`) | **20 tests, 0 failed** |
| `./gradlew :desktopApp:compileKotlin` | **BUILD SUCCESSFUL** |
| Integrator step 2: one Android `ExternalLinkOpener` binding | exactly one (`AndroidExternalLinkOpener` in `PreviewQrWiring.kt`) |

## 2. Failures fixed (inside the unit's write set)

| Test | Cause | Fix |
|---|---|---|
| `PreviewScreensTest` — B-SEC-058 the running page fills the screen under Preview with its port | `DevPreviewScreen` passed `Modifier…testTag("dev-preview-page")` into the slot; the slot's own `testTag` lands on the same semantics node and the outer tag wins, so the page's own tag was hidden. Real defect for any slot that tags itself. | `DevPreviewScreen.kt`: the screen's tag now sits on a wrapping `Box`; the slot gets a plain `fillMaxSize()` modifier. |
| `PairingScreensTest` — B-PROTO-069 scanning shows the instruction and a non-pairing QR is refused plainly | same shadowing in `PairingScannerScreen` (`"pairing-viewfinder"` over the camera slot's tag) | `PairingScannerScreen.kt`: same wrapping `Box`. |
| `PairingScreensTest` — B-PROTO-064 the server form offers Scan code and fills itself from a pairing code | the new Scan code / Pairing code / notice rows push **Save and connect** below the test window, so the injected click missed it (`calls == [scan]`) | test scrolls to the button before clicking (`performScrollTo()`); the assertion (scan, then save with the filled address/user/password) is unchanged. |

No assertion was weakened and no test was removed.

## 3. Files changed outside the unit's write set, and base failures left alone

| File | Change | Why |
|---|---|---|
| `native/quality/src/test/.../ChatWorldReviewTest.kt` | pass `filePickers = emptySet()` to `BasicComposerViewModel` | base `8a4eee69` added a required `filePickers` constructor parameter but did not update this test, so `:quality:test` could not compile. One line; blocks the required gate. |

Base failures **not** fixed (outside the unit, in the base's "unqualified model/file composer WIP", owned by the
composer unit):

- `:feature:chat:compileTestKotlinDesktop` fails on base test files the unit never touched:
  `ComposerDraftViewModelTest.kt:42` (missing `filePickers`), `WorldComposerFilesModelsTest.kt:163` (fake gateway lacks
  `createSelectedSession` / `setSessionAgent`), `ComposerFilesViewModelTest.kt:44,46` (subclasses the final shared
  `FakeChatWorld`, hides its `calls`). Fixing these means finishing that unit's test design. Consequence: the unit's
  `ChatEntries.kt` / `ChatScreen.kt` edit (the **Preview a running app** bar action) is **compiled** (desktop and
  Android, inside the APK) but **not exercised by chat tests**.
- `:quality:test` K1: `BasicComposerHost.kt` imports `org.jetbrains.compose.resources.StringResource` (the type),
  which `KitOnly` does not allow in features.

## 4. Commits

1. `fix(native): PREVIEW-QR screen tags no longer hide the slot's own semantics; form test scrolls to Save [skip ci]`
2. `fix(native): quality ChatWorldReviewTest passes the base's new filePickers parameter; P12V report [skip ci]`

## 5. Remaining gaps

- Emulator/device steps from the unit report §5.3 were not run: E2E-02 steps 1, 4, 5, 6 (virtual-scene QR,
  `pm revoke … CAMERA`), the dev-server egress checks (POST form, iframe and Vite HMR websocket must fail; a
  cross-origin link must ask first), and P-M3 (viewfinder orientation, first-lock time) on a real device.
- B-PROTO-068 / B-SEC-108 (address fallthrough, per-address reasons) still missing, as the unit reported: needs a
  probe call in `:engine:connection`.
- Feature-module Android host tests (`testAndroidHostTest`) were not run; the feature modules' tests are common/desktop.
- The base chat test sources and K1 (§3) need the composer unit's owner before `:feature:chat:desktopTest` and
  `:quality:test` can be fully green on this line.
