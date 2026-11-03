Verdict: FAIL — 0 BLOCKER, 7 MAJOR, 2 MINOR.

1. **MAJOR — Pairing discards fallback addresses.** [PairingInbox.kt:34](/home/eslam/Storage2/wt/codex-review-p12/native/feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/pairing/PairingInbox.kt:34)  
   A code containing an unreachable HTTPS address followed by a reachable HTTPS address always fills the first. The remaining addresses are discarded before Save, so retrying the code cannot recover. Flutter probes candidates and selects the working address. **Rows: B-PROTO-068, B-SEC-108.**

2. **MAJOR — A late camera result can pop two screens.** [PairingScannerViewModel.kt:64](/home/eslam/Storage2/wt/codex-review-p12/native/feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/pairing/PairingScannerViewModel.kt:64)  
   Camera delivery is posted to the main thread without a disposal check. If Close or a successful paste pops the scanner before that callback executes, `scanned()` still writes the inbox and calls `nav.back()` again, popping the server form. Neither `close()` nor `deliver()` marks the scanner finished. **Row: B-PROTO-069.**

3. **MAJOR — Cancelling scanning loses the server draft.** [ServersScreen.kt:112](/home/eslam/Storage2/wt/codex-review-p12/native/feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/ui/ServersScreen.kt:112)  
   Enter a name, address and credentials, open Scan code, wait for navigation to finish, then close it. All fields use ordinary `remember`; Nav3 removes the form composition, and returning recreates empty fields. Cancelling the scanner therefore destroys the unsaved form. Flutter retains its editor beneath the scanner. **Row: B-PROTO-069.**

4. **MAJOR — Mentioned previews lose their path and query.** [PreviewRoute.kt:21](/home/eslam/Storage2/wt/codex-review-p12/native/ui/routes/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/routes/PreviewRoute.kt:21)  
   For `http://localhost:5173/app?mode=demo`, the menu retains only port and scheme. Opening it reconstructs `/`, so applications served beneath `/app` or requiring the query open incorrectly. Flutter carries the complete `DevPreviewLink` through selection. **Rows: B-CHAT-025, B-CHAT-027.**

5. **MAJOR — Tapped localhost links bypass preview routing.** [ChatFeelWiring.kt:66](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/ChatFeelWiring.kt:66)  
   A reply link such as `http://localhost:5173` goes through the external-browser confirmation without being claimed by preview. For a remote conversation, the browser consequently opens the phone’s localhost instead of the remote server’s port. Flutter claims these links and resolves them against the conversation’s server. **Rows: B-CHAT-025, B-CHAT-027.**

6. **MAJOR — Same-origin redirects break relative resources.** [KitWebPreview.kt:224](/home/eslam/Storage2/wt/codex-review-p12/native/android/webview/src/main/kotlin/io/github/eslamasabry/oc/android/webview/KitWebPreview.kt:224)  
   If `/` redirects to `/app/`, the proxy fetches `/app/` internally but returns its body as the response for `/`. A relative `src="bundle.js"` then requests `/bundle.js` instead of `/app/bundle.js`; relative navigation and the displayed/browser address are wrong too. Flutter lets WebView follow the redirect. **Row: B-CHAT-027.**

7. **MAJOR — Same-origin writes are blocked, breaking ordinary preview interaction.** [PreviewRules.kt:124](/home/eslam/Storage2/wt/codex-review-p12/native/feature/preview/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/preview/presentation/PreviewRules.kt:124)  
   A preview application submitting a login form or calling `POST /api/...` receives the interceptor’s 405, even on the permitted host and port. Flutter permits these same-origin requests; the catalog boundary restricts destinations and credentials. **Row: B-SEC-058.**

8. **MINOR — Ports mentioned only in tool output disappear.** [ChatEntries.kt:71](/home/eslam/Storage2/wt/codex-review-p12/native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ChatEntries.kt:71)  
   When a shell tool prints its localhost URL and the assistant does not repeat it, Preview lists no mentioned port. Collection reads only prompt and assistant block rows; Flutter explicitly includes tool output. **Row: B-CHAT-026.**

9. **MINOR — Scripted navigation can raise external-link prompts.** [KitWebPreview.kt:144](/home/eslam/Storage2/wt/codex-review-p12/native/android/webview/src/main/kotlin/io/github/eslamasabry/oc/android/webview/KitWebPreview.kt:144)  
   `location.href` to another host triggers the browser confirmation without a user gesture; intercepted HTTP redirects do likewise at line 221. This allows page-controlled unsolicited prompts. Plan 16 §1.8.5 explicitly requires silent refusal without `hasGesture()`. **Row: B-SEC-058.**

Static review only; no builds or device tests run. Checkout unchanged.