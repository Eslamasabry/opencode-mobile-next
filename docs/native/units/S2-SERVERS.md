# Unit S2-SERVERS: servers, editor, pairing code, connection details, switcher, cleartext consent, pin review (report)

Branch `native/servers-2026-11-01`, from `native/m0-2026-11-01` at `6b9a2b212`. Date: 2026-11-01.
Plan: `plan/15` row S2-SERVERS, `plan/07` §5 E (E01, E02, E04) and §2.3–§2.5, `plan/04` §4.2–§4.5 and WP-04.9,
`plan/16` (secrets never printed). Write set: `native/feature/servers/**` (new module) plus this report.

Finish line (roadmap): *Servers, the editor, typed address and pairing code, connection details, the switcher, cleartext
consent and pin review work; E2E-03 and E2E-04 pass.*

Status, using the separate shipping states from AGENTS.md:

| State | Where it stands |
|---|---|
| Implemented | Yes, inside `:feature:servers`: presentation, ViewModels and screens. |
| Enabled in the app | **No.** `ServersRoute` doesn't exist in `:ui:routes` yet. `S1-CONN` and the profiles engine aren't on the branch to implement `ServersPort`. `KitField` isn't in the kit. See requests R1–R4. |
| Verified | Module level only: `:feature:servers:desktopTest` runs 75 tests with 0 failures, and `verifyRoborazziDesktop` passes on 28 goldens. **E2E-03 and E2E-04 were not run.** They need the app wiring, the emulator and FX-TLS, so the finish line is **not met**. |
| Committed and pushed | Yes, on `native/servers-2026-11-01`. |
| Deployed or released | No. |

## What was built

All code is in `native/feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/`.

### `presentation/`: no Compose and no copy (rule 3, K2 clean)

- **`ServersPorts.kt`** holds the feature-local ports. The engines this unit depends on aren't on M0, so these name the
  narrow surface the screens need. The app binds them once those engines land:
  - `AddressPolicy` exposes the `:core:security` `AddressRules` the screens use. Features may not import
    `:core:security` (K1). Production forwards to `AddressRules`, and tests bind that same forwarder
    (`RulesAddressPolicy`), so every row runs against the real rules.
  - `ServersPort` covers the profiles and connection surface: `servers: StateFlow`, `probe`, `save`, `connect`,
    `disconnect`, `forget`, `inspectCertificate` and `trustCertificate`. All are `suspend … Outcome` (rule 4).
  - `ServersNavigator` is the navigation the feature asks for: `openEditor`, `openServerPage`, `land`,
    `resetToServers` and `back`, following the 07 §2.4 table.
  - The value types are `SecretText` (it prints `<redacted>`), `ProbeTarget`, `ServerDraft`, `CertificateSummary`,
    `SavedServer` and `EditorMode`.
- **`ConnectionDiagnosis.kt`** ports `lib/ui/widgets/connection_failure.dart` from error *strings* to the typed
  `Failure` (B-NAV-007…009). The output is `ConnectionFailureUi(kind, checks, primary, tailnet, details)`. Every
  field is an enum or a sealed type, so no server or exception text can reach the copy. The redacted `details` is
  shown only under "Technical details".
- **`Pairing.kt`** ports `lib/state/pairing.dart`:
  - `PairingCode.parse` and `looksLike` cover the exact `opencode2 pair` JSON shape. They return 13
    `PairingRejection` reasons and never echo the input.
  - `PairingPayload` prints a redacted password, strips user info, query and fragment from addresses, hides a
    non-default username, and has `consume()`. Its `ImmutableList` addresses can't be changed (B-SEC-107).
  - `PairingDialer` never dials an address with credentials or an unconfirmed plain-HTTP address. It holds those
    with `NeedsCleartextConsent` (B-SEC-124), tries routable addresses first and stops at the first that answers.
    When nothing answers, it gives one fixed `AddressVerdict` per address (B-SEC-108).
- **`ConnectMethods.kt`** sets the order: headless first, key next, browser-redirect last, with hints (B-NAV-006).
- **`ServersUi.kt`** holds the screen contracts (`XUi` + `XActions`) for Servers, the editor, the server page and the
  switcher. `EditorUi.toString()` redacts the pairing code, which holds the password; a test caught that leak.
- **ViewModels.** Each takes a `CoroutineScope` through `ViewModel(scope)`, so tests drive them on a test dispatcher.
  - **`ServersViewModel`** puts the phone's rows first. Each row's address is `scheme://host:port`. Connect success
    calls `land()`. A failure stays on its row, diagnosed, and its action leads to the fix: Credentials with
    `focusPassword`, Edit, or Retry. Forget asks first, then calls `port.forget`, which is the profile sweep.
  - **`EditorViewModel`** handles a typed address or a pairing code:
    - A plain-HTTP origin that needs consent is **held and never probed** until the person allows it, per origin and
      per port. The question uses the gentler sentence for private and Tailnet hosts and the stronger one for public
      hosts.
    - A certificate is trusted only after `inspectCertificate` showed its fingerprint and the person chose Trust.
      Closing the review never grants trust.
    - `checkPin` refuses a pin on HTTP.
    - In Edit mode, a changed host, port or scheme drops the pin (B-SEC-154).
    - The saved password is never prefilled ("Saved · Replace", K11).
    - Attempts are counted per origin (B-NAV-009).
    - **Saving never happens without a successful test.** A save is followed by `connect`, then `land()`.
    - The pairing password is consumed after save and when the editor goes away.
  - **`ServerPageViewModel`** covers the page and its connection details:
    - It shows the transport security (`ThisDevice`, `Https`, `HttpsPinned`, `HttpAllowed` or `HttpNotAllowed`)
      and the pin as an `AA:BB:…` fingerprint.
    - Disconnect resets to Servers. Forget asks first and then resets to Servers.
    - A changed certificate is reviewed with the old and new fingerprints side by side before `trustCertificate`.
    - Its state is `null` once the profile is gone (N12).
  - **`ServerSwitcherViewModel`** puts the current server first and shows a port only when two saved servers share a
    host (B-NAV-041). It only chooses: it closes first (`back()`), then connects and lands, or resets to Servers on
    failure. Choosing the current server only closes the sheet.

### `ui/`: kit parts only (K1 clean)

- **`ServersScreens.kt`** has `ServersScreen`, `EditorScreen`, `ServerPageScreen`, and `ServerSwitcherContent` with
  `ServerSwitcherSheet`. They are built only from `KitScreen`, `KitRow`, `KitButton`, `KitText` and `KitSheet`. Rows
  that open pages are visible rows, and every action names its server ("Disconnect from Laptop", "Remove Laptop from
  this phone").
- **`ServersParts.kt`** has:
  - `FailureVerdict`: headline, sentence, checks, one action, and a "Technical details" row that opens the redacted
    text.
  - `CleartextConsentSheet` and `PinReviewSheet`: the fingerprint is shown in full with its validity dates, and the
    old fingerprint appears when the certificate changed.
  - `LocalServersField`: the input slot (deviation D2).
- **`ServersWords.kt`** maps each enum to an **existing** `:ui:words` key (ported Flutter copy). No string is authored
  in this module.

## Catalog rows covered (tests named after the rows; rule 11)

| Row | Tests |
|---|---|
| B-NAV-006 | `ConnectMethodsTest` (2) |
| B-NAV-007 | `ConnectionDiagnosisTest` (9), `ServersViewModelsTest` (inline failure + fix), `ServersScreensTest` (visible text: "Password rejected", Update password, raw text only under Technical details) |
| B-NAV-008 | `ConnectionDiagnosisTest` (7), `EditorViewModelTest` (trust only after the fingerprint is shown), `ServersViewModelsTest` (changed certificate reviewed against the old pin), `ServersScreensTest` (fingerprint shown before "Trust this fingerprint") |
| B-NAV-009 | `ConnectionDiagnosisTest` (5: three attempts, details only, phone server stopped, in-app server, OpenCode 1), `EditorViewModelTest` (Test connection ×3) |
| B-SEC-107 | `PairingTest` (8), `EditorViewModelTest` (code → save → land, password not in state), `ServersScreensTest` |
| B-SEC-108 | `PairingTest` (4), `EditorViewModelTest`, `ServersScreensTest` (each address with its own reason) |
| B-SEC-124 | `PairingTest` (4), `EditorViewModelTest` (3), `ServersScreensTest` (2: private and public sentences; nothing dialed before Allow) |
| Partly, from the E2E-04 steps that are this feature's half | B-SEC-120 (loopback and https never ask; a bare private address gains https), B-SEC-153 (per-port consent; credentials, query and fragment refused), B-SEC-154 (a host change drops the pin; the page shows the pin), B-NAV-041 (port only for a shared host) |
| Feature half of E2E-01 | the welcome; connect or save success → `land()`; Disconnect → `resetToServers`; forget asks first; the switcher closes first |

Goldens (28): `ServersScreen`, `ServersWelcome`, `EditorCleartextConsent`, `EditorPinReview`, `EditorFailure`,
`ServerPage` and `ServerSwitcher`, each in light and dark, English and Arabic (RTL), at 412 × 915. They are in
`native/feature/servers/src/screenshots/`.

## Commands and results

Commands were run in `native/`. The environment was `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and
`ANDROID_HOME=/opt/android-sdk`. The SDK came from `commandlinetools-linux-16111833_latest.zip` with licences
accepted, then `platform-tools`, `platforms;android-37.1` and `build-tools;37.0.0` were installed.

| Command | Result |
|---|---|
| `./gradlew :feature:servers:compileKotlinDesktop --max-workers=2` | BUILD SUCCESSFUL |
| `./gradlew :feature:servers:desktopTest --max-workers=2` | BUILD SUCCESSFUL: **75 tests, 0 failures**. By suite: ConnectionDiagnosis 21, Pairing 17, EditorViewModel 11, ServersViewModels 8, ServersScreens 9, Goldens 7, ConnectMethods 2. |
| `./gradlew :feature:servers:recordRoborazziDesktop --max-workers=2` | BUILD SUCCESSFUL: 28 PNGs |
| `./gradlew :feature:servers:verifyRoborazziDesktop --max-workers=2` | BUILD SUCCESSFUL |
| `./gradlew :quality:test` | **Did not run.** The build fails in `:engine:connection:compileKotlinDesktop` (`ConnectionRegistry.kt:48`, `ConnectionSession.kt:26`), which is outside this unit and is the core-contract reconciliation the brief warned about. K1 and K2 were instead checked by a script that mirrors `KitOnly.violations`: every `commonMain` file is clean, and only test files are flagged (request R5). |
| `git diff 6b9a2b212 \| grep` for private IPv4 ranges and e-mail addresses | Clean after commit `b0fc266ea`. The test vectors now use `desk.local`, `attic.local` and `laptop.tail1234.ts.net`, which the same rules classify as private network and Tailnet. Two kinds of hit remain on purpose. The first is `127.0.0.1`, which is loopback and not a private-network address. The second is URL-userinfo vectors such as `https://user:pw@desk.example`, which are the "credentials in the address" test inputs of B-SEC-107/153 and are not e-mail addresses. |

Commits: `d5ae8fb64` (presentation and tests), `38510239d` (screens, screen tests, goldens), `b0fc266ea` (no IP
literals), then this report. All carry `[skip ci]`.

## Deviations from the plan

- **D1. Goldens are on Compose Desktop, not Robolectric.** `oc.kmp.library` adds `androidHostTest` only to the
  platform modules (`ModuleRules.platformSourceSets`). This module therefore uses Roborazzi's
  `roborazzi-compose-desktop` in `desktopTest`; the version comes from the catalog pin, like `:ui:kit` does with its
  test-only artifacts. Android renders join when `:ui:testing` or a feature host-test convention exists (R7).
- **D2. There is no `KitField`, so the editor's inputs go through `LocalServersField`.**
  - Rule 2 forbids drawing a field in a feature, and `KitField` (06 §16, WP K1) isn't in `:ui:kit`.
  - The editor therefore asks the host for its input part through a CompositionLocal. The production default shows the
    label and typed value as kit text, and never a secret.
  - Screen tests supply a `BasicTextField` stand-in in **test code only**.
  - When S1-KITA2 lands, the default becomes `KitField(kind)` in one place.
- **D3. There is no `ServersEntries` or `ServersRoute`.** `AppRoute` is a sealed interface in `:ui:routes`, which the
  coordinator owns, so the feature can't add routes. ViewModels talk to `ServersNavigator` instead. The Entries file and
  an `AppNavigator` adapter follow R1.
- **D4. The ViewModels have no Metro annotations yet.** `ServersPort` and `AddressPolicy` have no bindings, and
  `:feature:servers` is in neither app, so `@AssistedInject` and `@ContributesIntoMap` are added in the wiring change.
  Adding them now would break the app graph the moment the module is included.
- **D5. Plain HTTP to a private address asks first.** This follows plan 04 §4.3, where every non-loopback `http` needs
  per-origin consent and the private wording is the gentler one. That contradicts the B-SEC-120 row text and E2E-04
  step 2 ("accepted without a prompt"), which 04 §15.5 says is corrected. B-SEC-124 agrees with 04.
- **D6. Pairing parsing and dialing live in the feature.** Plan 04's table puts them in `:protocol:oc2`
  (`Oc2PairingCodecTest`) and `:engine:connection` (`PairingDialerTest`), and neither exists in a usable form. The code
  is pure, has no dependencies and is ready to move with its tests.
- **D7. No formatter was run.** `tool/format-changed.sh` (rule 13) isn't on the branch, and the build has no
  ktlint, ktfmt or spotless. The files follow the surrounding style by hand.
- **D8. The editor is simplified against 07 §5 E02:**
  - There is no 800 ms auto-test.
  - The steps are one in-page entry choice: pairing code or address.
  - QR scanning is out of scope (non-goal, S3).
  - The Codex token reuses the password field.
- **D9. Local build environment only, nothing committed.** Maven Central kept answering `429 Too Many Requests` to
  this shared egress. A `~/.gradle/init.d/mirror.gradle.kts` puts Google's Maven Central mirror first, and a retry
  wrapper re-runs a build on a download error.
- **D10. `ConnectionDiagnosis.UPGRADE_COMMAND` is a literal in presentation.** It is the Dart `openCode2UpgradeCommand`:
  a shell command shown verbatim, not copy.

## Catalog and coordinator requests

- **R1 (`:ui:routes`, coordinator).** Add the 07 §2.2.2 rows 30–34:
  - `ServersRoute.Servers` (Root)
  - `Editor(mode, connectOnSave, focusPassword)`
  - `ServerPage(profileId)`
  - `ConnectionCheck(profileId)`
  - `PairingScanner`

  Once §2.3 lands, also add `SheetKey.ServerSwitcher`, `SheetKey.ConnectionDetails` and `ServersArrival`. This unit then
  adds `ServersEntries.kt` plus an `AppNavigator` → `ServersNavigator` adapter: `openEditor` → `open(Editor)`,
  `resetToServers` → `resetTo(Servers)`.
- **R2 (app graph / core owner).** Bind `AddressPolicy` to `AddressRules`. The binding is the 20-line forwarder in
  `commonTest/RulesAddressPolicy.kt`. An alternative is to move `AddressPolicy` into `:core:domain` so features stay
  off `:core:security`.
- **R3 (S1-CONN and the profiles engine).** Implement `ServersPort` with this behaviour:
  - Keep the last connect failure and the attempt count on `SavedServer`.
  - Mark `inAppServer`.
  - `probe` honours `ProbeTarget.consentedOrigin` and `pinnedSha256`, through plan 04 §4.6's trust gate.
  - `inspectCertificate` is plan 04 §4.5 (no HTTP bytes).
  - `trustCertificate` writes `oc.certificatePin.<id>`.
  - `save` writes `oc.cleartextOk.<id>` from `cleartextConsent`.
  - `forget` is the `ProfileScope` sweep.
- **R4 (kit owner, S1-KITA2).** Add `KitField` (Url, Text, Secret, Mono). Then the default of `LocalServersField`
  becomes `KitField`.
- **R5 (coordinator, `:quality`).** Konsist K1 scans feature **test** files. Any screen or coroutine test imports
  `androidx.compose.ui.test.*`, `kotlinx.coroutines.test.*`, Roborazzi or a `KitPlatform` fake, so K1 can't hold for
  `chats` either once it gains such tests. Proposed fix: slice K1 to `commonMain`, `androidMain` and `desktopMain` the
  way `production` already does, or add `:ui:testing` and allow it.
- **R6 (`MODULE_OWNERS`, coordinator).** Add `:feature:servers	F	S2-SERVERS	slice-2`, so `ModuleOwnersTest` knows the
  new module.
- **R7 (build-logic owner).** Choose one of two ways to get Robolectric goldens for features:
  - a host-test option for feature modules (`withHostTest` plus the JDK 21 `--add-opens` that `:ui:kit` sets
    locally), or
  - `:ui:testing`.

  Separately, add catalog entries for the test-only `roborazzi-compose-desktop`, `roborazzi-compose`,
  `ui-test-junit4` and `ui-test-manifest`.
- **R8 (words owner).** Every word is an existing key, but some were borrowed from other areas or are missing:
  - **Missing words:**
    - a line for plain HTTPS on the server page (this unit shows nothing for `TransportSecurity.Https`)
    - "Save and connect" (the editor uses `settingsV3ConnectTo`)
    - a "Previously trusted" label (it borrows `serverTrustCertificateChangedTitle`)
    - a pin-on-HTTP refusal (it borrows `e7SharedHTTPIsNotEncryptedOtherDevicesOn`)
    - the Dart pairing reasons "The connection was refused…" and "This address uses plain HTTP. Confirm that first;
      nothing was sent to it." (`cleartextConfirmReason`)
  - **Keys borrowed from other areas** (`servers`-named keys would read better): `connectorCardConnecting`,
    `externalAgentsRemoveNamed`, `agentsRemoveTitle`, `settingsV3ConnectTo`, `externalAgentsDetailConnection` and
    `e7VoiceUiTechnicalDetails`.
- **R9 (coordinator).** Add `:feature:servers` to `:androidApp` and `:desktopApp` after R1–R3.

## Open problems

- **The finish line is not met.** E2E-03 and E2E-04 need R1–R3, the emulator and FX-TLS. The pieces that can be
  proven in the module are proven. The wire half belongs to WP-04.3 and WP-04.9: no Basic header over plaintext,
  redirects not followed, latched pins, P-N2.
- **`:quality:test` is blocked** by `:engine:connection` not compiling on M0. Re-run it after the integration session,
  together with R5 and R6.
- **Not built yet** (07 §5 E01 and E02):
  - the `EnterPhoneCredentials` restore-first rule
  - the welcome's "this phone" and "my computer" choice
  - the root menu (About, Report a problem, Setup guide)
  - `ConnectionCheck` (E05)
  - the queued-prompt and draft counts in the forget confirmation (`serversRemoveQueued*`, `serversRemoveDrafts`)
  - phone rows marked "This phone" (B-NAV-040)
- **Design review.** When a failure shows in the editor, both "Try again" and "Connect to …" are drawn as accent
  buttons. The "Connect to …" label uses the address as typed. Both are worth a kit-owner look.
