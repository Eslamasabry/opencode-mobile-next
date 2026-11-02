# Unit P12 PREVIEW-QR: dev preview with hardened egress, server pairing by QR and by link

Date: 2026-11-02 (parity push). Base: `native/m1-2026-11-03` at `8a4eee69`.
Branch actually pushed: **`claude/preview-qr-pairing-57sjh4`** (the brief named `native/p12-previewqr-2026-11-03`; this
session may push only to its designated branch, so the integrator should merge or rename from the pushed one).

Finish line: a person can open a running dev server from a conversation inside the app, safely, and can add a server
by scanning the `opencode2 pair` QR, pasting the code, or pasting a share link, which fills the server form.
Non-goal: probing several pairing addresses in turn (see §6).

## Shipping state — read this first

| State | |
|---|---|
| Implemented | yes: everything in §1 |
| Compiled with Gradle | **no**: this container cannot install the Android SDK or resolve AGP/androidx (the network policy refuses `dl.google.com`, which also serves Google Maven). No Gradle task could configure. |
| Verified here | pure logic and ViewModels compiled with a standalone `kotlinc` 2.2.21 against the real `core/common`, `core/security`, `engine/connection` and `ui/routes` sources (Metro/lifecycle annotations stubbed): **49 tests, 0 failures** (§4). `KitWebPreview.kt` compiled against the Android 16 framework jar (`org.robolectric:android-all`). |
| Not verified | Compose screens and their desktop tests, `PreviewQrWiring.kt`, `:androidApp:assemblePreviewDebug`, the emulator. |
| Committed / pushed | yes, three commits, all `[skip ci]` |
| Released | no |

## 1. Journeys now wired

| Journey | How to reach it | What happens |
|---|---|---|
| Preview a running app from a conversation | Chat → bar action **Preview a running app** | `PreviewRoute.Ports`: the dev-server ports the conversation mentioned (newest first, one per scheme/port, up to 5), each saying **On this phone** or **On <host>**, plus a typed **Port number** field and **Open port** as the always-visible path. |
| The running page | tap a port | `PreviewRoute.Page`: bar "Preview", "Port N" under it, **Refresh page** and **Open in browser**. Tapping the title opens **Preview details** (the real address and, for a remote server, the "listen on 0.0.0.0" note). Back steps back through the page's own history first. "Nothing is answering yet / Try again" when the port does not answer. No server → "Connect a server first". Desktop → "Preview isn't available here" with Open in browser. |
| Leaving the preview | a link inside the page to another address | It never loads in the app; **Open this link in your browser?** names the host; **Open in browser** / **Don't open**. Unsafe links are refused in plain words. |
| Pair by QR | Conversations gear → Servers → **Scan code** | `PairingRoute.Scanner`: camera page with the instruction and privacy line; the first pairing QR fills the server form and closes the page. A QR that is not a pairing code is refused in one plain sentence and scanning continues. Camera refused → "Camera access is needed to scan" + **Allow camera**; refused for good → "Camera access is turned off" + **Open app settings** (rechecked on return); no camera → "This device has no camera". The **Pairing code** field (with the kit's Paste) is on every stage. |
| Pair by pasting a code | Servers → **Pairing code** field (Paste), or paste into the address field | The form fills: address (the one a phone should dial first), username, password; "Filled in from the pairing code. Check it, then save." Broken codes say exactly what is wrong (`e7SetupPair*` sentences), never echoing the input. Nothing is contacted until **Save and connect**; plain HTTP still needs the existing per-address consent switch. |
| Pair by share link | paste an `opencode-mobile://session/v2?…` link into the same field | Only the private `*.ts.net` HTTPS origin is taken: "Filled in the address from the link. Add the password, then save." |

## 2. Files

| Where | Files | Notes |
|---|---|---|
| `native/feature/preview/**` (new module) | `PreviewEntries`, `presentation/{PreviewRules,PreviewUi,PreviewViewModels,PreviewBrowser}`, `ui/{DevPreviewScreen,PortsScreen,WebPreviewSlot}` | `PreviewEgress` (same host:port, http(s), no credentials; GET/HEAD only; document CSP with no `'self'`, no frames/workers/objects, connect-src only the origin's http(s) → no websockets), `DevPreviewTarget.resolve` (Flutter rules incl. leases), `WebOrigin`. Slots: `WebPreviewSlot` (default unsupported), `PreviewBrowser` (default refuses). |
| `native/android/webview/**` (new module) | `KitWebPreview.kt`, manifest | The only `WebView(` (K-SEC-9 file name). Every request is checked: other origin → 403 refusal, non-GET/HEAD → 405; main-frame documents are fetched here (redirects followed only within the origin, cookies bridged to `CookieManager`) and served with the CSP; no JS bridge, file/content access off, geolocation/camera/mic denied, no pop-ups, mixed content never, downloads ignored; cookies/cache/storage cleared on open and close. Manifest adds `CAMERA` + `uses-feature camera required=false` for the scanner. |
| `native/feature/servers/**` (new files) | `pairing/{PairingPayload,PairingInbox,QrScannerSlot,PairingScannerViewModel,PairingScannerScreen,PairingEntries}` | Parser (Flutter `lib/state/pairing.dart`), `ShareLink` (Flutter `SessionAddressLink` strict rules), memory-only `PairingInbox`, scanner page. |
| `native/ui/routes` (new files) | `PreviewRoute.kt`, `PreviewLinks.kt`, `PairingRoute.kt` | Keys hold a port and scheme only — no host, path or conversation text. `PreviewLinks` is the one link scanner (chat and preview share it). |
| `native/ui/words` (new files) | `values{,-ar}/strings_previewqr.xml` | 9 new EN+AR strings; everything else reuses existing `devPreview*`, `e7Setup*`, `externalLink*`, `pairingScanner*` strings that already had Arabic. |
| App wiring | `androidApp/.../graph/PreviewQrWiring.kt`; 4 lines in `androidApp/build.gradle.kts` | `AndroidExternalLinkOpener` (ACTION_VIEW + BROWSABLE; **there was no Android `ExternalLinkOpener` binding before**), `PolicyPreviewBrowser`, `AndroidWebPreviewSlot`, `AndroidQrScannerSlot` (`:android:camera` `CameraXScanner` into a TextureView, scoped lifecycle so the camera never outlives the page, permission in three states). |

### Edits outside the unit's own folders (smallest possible, each needed)

| File | Edit | Why |
|---|---|---|
| `feature/chat/ChatEntries.kt`, `ui/ChatScreen.kt` | `ChatScreen(extraActions = …)` param appended to the bar; the entry injects `AppNavigator` and adds **Preview a running app** | the reachable door; computes `PreviewRoute.Ports.from(ref, row texts)` |
| `feature/servers/presentation/{ServersUi,ServersViewModel}.kt`, `ui/ServersScreen.kt` | `pairingArrival`/`pairingProblem` in state; `scanPairing`/`pastePairing`/`takePairing` with default bodies; VM takes `PairingInbox` with a default; form gets **Scan code**, the **Pairing code** secret field, fill-from-inbox and pasted-code detection in the address field | "filling the server form"; existing tests and fakes compile unchanged |
| `gradle/libs.versions.toml` | `camerax` 1.6.2, `zxing-cpp` 3.1.1 and the four aliases | turns on `:android:camera`'s compile-verified adapters (QR-CAMERA §6 request) |
| `build-logic/.../ExternalRules.kt` | allow `androidx.camera` for `:android:camera` and `:androidApp`, `io.github.zxing-cpp` for `:android:camera` | same; the app needs `SurfaceRequest` to draw the viewfinder |
| `MODULE_OWNERS` | rows for `:feature:preview`, `:android:webview` | `ModuleOwnersTest` |

## 3. Catalog rows

| Row | Covered by |
|---|---|
| B-CHAT-025 | `PreviewRulesTest` (parse), `PreviewViewModelTest` (typed port), `PreviewScreensTest` |
| B-CHAT-026 | `PreviewRulesTest` (scan, recent, route keeps only ports), `PreviewViewModelTest`, `PreviewScreensTest` |
| B-CHAT-027 | `PreviewRulesTest` (phone/remote/lease/no server/no host), `PreviewViewModelTest`, `PreviewScreensTest` |
| B-SEC-058 | `PreviewRulesTest` (mayLoad, methods, CSP, origins), `PreviewViewModelTest` (address bar, browser through the policy, ask-first, refusal), `PreviewScreensTest`; enforcement in `KitWebPreview` |
| B-PROTO-064, 065, 066, 067 | `PairingTextTest`; 064/065 also `PairingFlowTest`, `PairingScreensTest` |
| B-PROTO-069 | `PairingFlowTest` (scan ≡ paste, non-pairing QR refused, Scan code opens the scanner), `PairingScreensTest` |
| B-SEC-107 | `PairingTextTest` (print redaction, consume, immutable list, fill consumes) |
| B-SEC-124 | `PairingFlowTest` (pairing never saves, contacts or consents); the existing per-address consent switch holds plain HTTP |
| B-SEC-157 | `PairingTextTest` (strict link, refusals), `PairingScreensTest` (fills address only) — parse side only; `encode` is not needed here |
| B-ANDR-049 | `PairingFlowTest`, `PairingScreensTest` (no camera, denied, blocked) — screen side; camera side was QR-CAMERA's |
| **Still missing** | **B-PROTO-068** (try addresses in order, fall through refused ones, 401 still counts) and **B-SEC-108** (per-address reasons): the native connection layer has no probe API (`ServerConnections` only saves/connects), so the form takes the best-ordered address and the person can paste again. E2E-02 steps on the emulator. |

## 4. Tests and results

| Run | Result |
|---|---|
| Standalone `kotlinc` 2.2.21 + JUnit 4: `PreviewRulesTest` (12), `PreviewViewModelTest` (10), `PairingTextTest` (13), `PairingFlowTest` (7) and the existing `ServersViewModelTest` (7) against my ViewModel edit | **49 passed, 0 failed.** The ported deep-nesting case first failed with a real `StackOverflowError` in kotlinx JSON's recursive reader; the parser now refuses nesting deeper than 32 before parsing. |
| `KitWebPreview.kt` compiled with `kotlinc -jvm-target 17` against `android-all-16-robolectric-13921718.jar` | compiled, no errors |
| Desktop Compose tests `PreviewScreensTest` (7), `PairingScreensTest` (7) | **written, not run** (needs Gradle) |
| `:feature:preview:desktopTest`, `:feature:servers:desktopTest`, `:feature:chat:desktopTest`, `:quality:test`, `:androidApp:assemblePreviewDebug` | **not run** (no Gradle) |

A manual K1/K2/K11 import check of the changed feature files against `KitOnly`'s allowlist found three violations
(`core.security`, `LocalClipboardManager`, `StringResource`), all fixed before the last commit.

## 5. What the integrator must do

1. Run `./gradlew :feature:preview:desktopTest :feature:servers:desktopTest :feature:chat:desktopTest :quality:test :android:camera:compileReleaseKotlin :androidApp:assemblePreviewDebug` and fix any compile slip in `PreviewQrWiring.kt` or the screens (none could be compiled here).
2. Check there is exactly one `ExternalLinkOpener` binding in the Android graph (this unit adds `AndroidExternalLinkOpener`; if another unit also adds one, keep one).
3. Emulator: E2E-02 steps 1, 4, 5, 6 (virtual-scene QR from `android/camera/src/test/resources/qr/pairing.png`; `pm revoke … CAMERA`), and a dev server on the emulator host for the preview (a POST form, an iframe and a Vite HMR websocket should all fail; a cross-origin link should ask first). P-M3 (viewfinder orientation, first-lock time) needs a device.

## 6. Deviations and open items

- **Address fallthrough (B-PROTO-068/B-SEC-108) not built:** needs a probe-only call in `:engine:connection` (owner). Until then the form gets the address a phone should dial first and says what it filled.
- **"Ask for a change…" field** of the Flutter preview is not ported: sending into the conversation needs the chat engine's steer path; out of scope for this unit.
- **Copy address** in Details is not offered: clipboard writes are kit-only (K-SEC-6) and there is no kit copy part for a row; the address is shown in full.
- **Paste** uses the kit's secret field (its Paste button exists only there), labelled "Pairing code" — accurate, since the code carries a password; it is also registered with the redactor by the kit.
- **Share-in / deep link** of `opencode-mobile://session/v2` into the app is not wired (manifest is the coordinator's); pasting it works.
- **Proxying page documents** loses the redirect target as the page's base URL when a same-origin redirect is followed internally; relative links on such pages may resolve against the original path.
- IPv6 dev-server hosts produce CSP sources browsers may ignore (host-source has no IPv6 literal), which then blocks the page's own XHR; IPv4/hostnames are fine.
- The viewfinder transform assumes CameraX's SurfaceTexture already applies sensor rotation (camera2 behaviour) and only fixes aspect; verify on a device.
