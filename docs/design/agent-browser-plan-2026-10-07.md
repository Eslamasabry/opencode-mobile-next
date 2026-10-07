# Agent browser: proposed plan, 2026-10-07

Status: **plan only; awaiting owner review; unavailable in the product**. Companion
[contract](agent-browser-contract-2026-10-07.md). Source inspection baseline:
`feat/agent-native-q`, `07a081c2`. No browser code, APK, device changes or runtime
qualification were performed for this plan.

**Finish line:** a qualified agent in the in-app Ubuntu opens its development
page in a visible, isolated browser pane, reads and operates that page through
bounded tools, while the person can take over or stop immediately; restart and
profile deletion revoke access and cannot resurrect browser state.

**Non-goals:** control of Chrome or personal accounts; hidden/background browsing;
arbitrary JavaScript execution, shell execution or CDP access; desktop/remote
agents; generic phone automation; uploads/downloads, payments or credential entry;
native implementation before this plan is approved. The other phone tools below
are later slices, not prerequisites for browsing.

## 1. Recommendation and decisions

Use a new `oc-browser` stdio MCP helper, installed only into qualified in-app
Ubuntu agent runtimes. It sends bounded request/reply messages to an Android-owned
filesystem Unix socket. Kotlin owns authentication, revocation, request bounds,
visibility and the WebView. Dart owns the profile/session association and the
kit-based pane state. Page JavaScript never gets the socket, a bearer token or a
native bridge. Existing `oc-ui` remains a display tool; do not overload its
non-blocking answer convention with browser RPC.

Use one pane and one active browser lease for the entire app initially. A lease
is tied to one profile, trusted session and native agent launch. Browser state is
a new disposable AndroidX WebView profile per lease. Require feature detection;
there is no fallback to the default WebView cookie store. The person sees the
owning conversation, destination origin, live control state, Take over and Stop
outside the web content. In narrow layouts the pane can stack with chat; it must
remain actually visible while commands execute.

V1 targets a person-approved local development origin first. Public HTTPS sites
follow in a separate qualification slice. Two security gates are unresolved:
trusted session binding of an MCP subprocess, and complete enforcement of the
chosen browser network policy. They must be demonstrated before enabling v1;
neither a tool-supplied session ID nor a WebView URL callback satisfies them.

## 2. Evidence from the current repository

References below are file:line at the baseline, not claims of runtime success.

| Finding | Evidence | Consequence |
| --- | --- | --- |
| Built-in Ubuntu is private app storage and uses native PRoot | `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt:42`, `:281` | Create a narrow native-owned socket bind; do not expose the full app directory to Ubuntu. |
| Agent PRoot has fake UID 1000, `/proc` and `/sys` binds, a separate `/root` view and a shared projects bind | `BuiltinLinux.kt:284`, `:289`, `:296`, `:301`, `:307` in the same directory | PRoot username and HOME do not establish an Android UID or session security boundary. Confinement tiers must be qualified separately. |
| Native path helper rejects linked descendants below a canonical app anchor | `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentPaths.kt:6` | Reuse that policy, strengthened with no-follow/descriptor-relative handling for socket and grant files. |
| Android channel ownership and disposal are centralized | `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt:55`, `:66`, `:137` | One owner changes both new browser channel halves and the small registration/disposal hook. |
| No existing WebView, platform-view factory or AndroidX WebKit dependency was found in `lib/`, native Kotlin, `pubspec.yaml`, or app Gradle | source search; dependency block `android/app/build.gradle.kts:129` | New native browser plumbing and dependency: new release, not a Dart patch. |
| Minimum Android API is at least 26; compile SDK is 37 | `android/app/build.gradle.kts:24`, `:36` | Platform API 28 profile suffix alone cannot be the universal path. |
| Global Android network policy permits cleartext | `android/app/src/main/res/xml/network_security_config.xml:14` | Do not infer WebView HTTP restrictions from manifest `usesCleartextTraffic=false`; apply a browser-specific policy. |
| External URL handling already has a single user-facing gate | `lib/ui/widgets/external_link.dart:48`, `:69` | All browser launch requests and external handoffs must preserve `openExternalLink`; see contract §6 for reuse and redirects. |
| `oc-ui` uses stdio request handling and Claude-only verification | `lib/builtin/agents/gen_ui_server.dart:174`, `lib/builtin/agents/gen_ui_install.dart:45` | Reuse installer ownership/qualification patterns, not its qualification result. It does not qualify bidirectional browser access. |
| MCP setup currently writes profile registration and invokes Claude user-scope add | `lib/builtin/agents/gen_ui_install_scripts.dart:267`, `:356` | Profile registration is not proof of the calling conversation. A session-scoped launch mechanism is a feasibility gate. |
| Existing deletion sweeps profile keys and has explicit shared cleanup | `lib/state/profiles.dart:459`, `lib/state/connection/deletion.dart:191` | Add grant revocation and native browser retirement; an installer cleanup failure must not prevent profile deletion. |
| Backup rules exclude private app data | `android/app/src/main/res/xml/backup_rules.xml:3` | Keep browser grants and content excluded; do not introduce export paths. |

## 3. Current Android API research and limits

The documented `LocalServerSocket(String)` uses the abstract namespace. It is not
a private filesystem path. Use a socket explicitly bound with
`LocalSocketAddress.Namespace.FILESYSTEM`, then a listening wrapper over that
bound descriptor. Descriptor lifetime needs explicit ownership: the
`LocalServerSocket(FileDescriptor)` wrapper does not own/close that descriptor.
Verify this exact sequence under the installed Android/PRoot combination.
[LocalServerSocket](https://developer.android.com/reference/android/net/LocalServerSocket),
[LocalSocketAddress.Namespace](https://developer.android.com/reference/android/net/LocalSocketAddress.Namespace).

`LocalSocket.getPeerCredentials()` supplies kernel peer credentials. Compare the
peer's real Android UID to the app UID before parsing authentication. This
excludes other ordinary apps; it does not distinguish agents sharing our UID.
It is an additional check, not the session identity mechanism.
[LocalSocket](https://developer.android.com/reference/android/net/LocalSocket).

AndroidX `WebViewCompat.setProfile` supports named profiles with a runtime
`MULTI_PROFILE` check and must run before other WebView operations. Select a fresh
name for every lease. Never copy cookies from another profile or Chrome.
[WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat).

Profile deletion has a material limitation: a loaded profile can be undeletable
within the current process, and deletion can complete asynchronously. Retire its
name immediately, destroy associated views, and sweep retired names before they
are loaded on the next process start. Do not promise synchronous secure erasure.
At most eight retired profiles may accumulate; require restart/cleanup before
creating more. [AndroidX ProfileStore](https://developer.android.com/reference/androidx/webkit/ProfileStore).

`WebView.setDataDirectorySuffix` is process-wide, API 28+, and must precede WebView
initialization. It is an alternative for a separately designed browser process,
not a per-tab isolation switch. Do not silently select that architecture if
AndroidX profiles are unsupported. [WebView](https://developer.android.com/reference/android/webkit/WebView).

No page-callable `addJavascriptInterface`, `postWebMessage` or message listener is
needed. Host-authored extraction scripts return only untrusted bounded data.
Keep file/content access off, TLS errors fatal, Safe Browsing on, and debugging
off. These are defense in depth; they do not turn a WebView into a permissionless
sandbox. [Native-bridge security guidance](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges).

URL callbacks are incomplete as a network firewall: navigation overrides omit
app-initiated loads and POSTs; resource interception does not cover every scheme
or redirect hop. Proxy override applies to all WebViews and has bypass behavior.
Therefore a callback-only implementation must not claim to block access to all
other local services. Phase B0 must choose and demonstrate a complete mechanism
for the supported network subset. A dedicated proxy is only a candidate until
WebSocket, worker, redirect, fail-open and WebRTC tests pass; it is not an approved
TCP fallback for MCP. [WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient),
[ProxyController](https://developer.android.com/reference/androidx/webkit/ProxyController).

The browser runs only while its pane is visible and the app is resumed/unlocked.
Android 15 `dataSync` foreground services have a shared six-hour/24-hour budget;
we will not keep a hidden browser alive by borrowing the existing connection
service. Background or lock immediately revokes automation and destroys the view.
[Foreground service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).

## 4. Trust and privacy model

Agent input, page DOM, console entries, URLs, titles, screenshots, tool results and
download suggestions are untrusted. Page text cannot grant permissions, change
the owning conversation or expand tool authority. No provider credential, app
cookie, Android account, clipboard or private file is supplied to a browser tool.
The socket transport never leaves the device, but browser output is returned to
the agent and may be transmitted to its configured model provider. The opening
consent states this before any capture or interaction.

App-private storage and UID checks protect against other normal apps. They do
not provide hostile same-UID isolation from arbitrary in-app Ubuntu processes.
Bearer grants bind authority and prevent accidental cross-session routing, but
a process that steals one has that authority until revocation. Session/process
confinement and token access must be qualified; no broad-root bind, world-readable
file, command-line secret or persistent MCP-config token is acceptable. If the
runtime cannot keep another profile from using the grant, report unavailable
for that runtime rather than advertise strong isolation.

Clicking and typing are not harmless display actions. They can submit forms and
trigger network side effects. Consent is for the named local development origin
and run, with an explicit interactive-control choice. No automatic reuse of
`oc-ui`'s show permission. V1 disallows credential/autofill/password entry, file
choosers, device permission prompts, downloads, external app intents, popups and
background media. The contract does not pretend to recognize every dangerous
button semantically; only disposable development pages belong in v1.

Human takeover cancels queued agent commands and invalidates element handles.
The person can inspect/interact without agent reads or screenshots. Returning
control requires an explicit action and disclosure of the current page; there
is no timer that returns control. Stop revokes the lease, blanks and destroys the
view, clears in-memory captures and retires the profile. It does not claim to
undo web actions already sent. Screenshots capture the pane viewport only, never
chat, notifications, keyboard, a chooser or the screen behind the app.

## 5. Slices and qualification gates

| Slice | Deliverable and finish line | Checks before it is enabled |
| --- | --- | --- |
| B0: bounded feasibility, no product enablement | Native proof selects exact socket API, authenticated per-session launch binding, profile support/cleanup, and an enforceable local-origin network policy; owner accepts the evidence and contract. | Emulator tests socket reachability from qualified PRoot, wrong-UID/expired/copied/cross-profile grants, path races, process death; adversarial network matrix includes redirects, IPv6, workers, WebSockets, POST, DNS rebinding, WebRTC and protected local service ports. No graceful fail-open. If a gate fails, stop and revise, not ship a weaker fallback. |
| B1: visible local-page read-only journey | Enable → person approves a local dev origin → pane opens → agent reads bounded text/DOM, screenshot and console errors → takeover/stop → restart/deletion leaves no authority. | Native plus Dart/unit/widget integration; real MCP round trip in emulator; verified profile separation; lifecycle race tests; no credential/log leakage. A shell helper alone is not completion. |
| B2: bounded interaction | Person explicitly grants interactive control for the lease → agent clicks/types/scrolls using current element handles → takeover wins races → output identifies completion vs unknown effect. | Stale handles, navigation mid-command, element changes, cross-origin frames, hidden/disabled/password fields, duplicate IDs, cancellation after dispatch and no automatic retry. |
| B3: explicit public HTTPS browsing | Owner-approved expansion to HTTPS origins with isolated browsing and the same takeover/deletion guarantees. | Separate network-policy proof, untrusted-site tests, third-party resources/redirect consent policy, no production-account defaults. Not enabled by B1's local proof. |
| B4+: individual phone actions | One consent-bound phone action per slice, start through completion/cancellation/deletion. | Contracts below; no blanket mobile-tools permission. |

The local network gate is intentionally before B1: this app runs sensitive agent
and orchestration services on loopback. A user-approved development port is not
permission for page scripts to call the others. The selected solution and its
native release cost must be added to the contract before B1 implementation.

## 6. Related phone tools, later scope

| Tool family | Reuse candidate | Required consent/result boundary |
| --- | --- | --- |
| Notify me | `lib/background/live_background.dart:341` | Per-profile opt-in and Android notification permission; rate limit; private lock-screen content by default; report posted/blocked, never claim the person read it. No background browser grant. |
| Share out | `lib/platform/share_intent.dart:78`, `MainActivity.kt:148` | Preview exact text/file, then person opens Android chooser. A chooser opening is not proof of sharing/delivery; cancellation is a valid result. No auto-recipient. |
| Open in another app | `lib/ui/widgets/external_link.dart:69` | Person taps exact safe URL; use existing gate. Never execute agent-supplied `intent:` or package names. File handoff requires a separate read-only temporary URI grant. |
| Read a picked file | Existing storage/project picker patterns; `lib/platform/storage_access.dart:23` is broad-storage permission, not this authority | System document picker, one selected file, bounded read, temporary opaque handle, no arbitrary host path or inherited all-files access. Revoke after operation/profile deletion. Show that content is sent to the agent. |
| Read aloud | `lib/voice/read_aloud.dart:65`, `:189`, `:270` | Preview, explicit play and always-available stop; queue/length limits; respect selected voice and potential network synthesis. No unsolicited speech on lock screen. |

## 7. Ownership, release and handoff

Proposed implementation units, assigned only after approval:

1. Native/channel owner: new Kotlin browser host, socket host, view factory,
   native lifecycle and both `oc/agent_browser` channel halves, registration in
   MainActivity, Gradle/manifest/security configuration. Existing channels remain
   owned as indivisible pairs.
2. Domain/helper owner: `lib/domain/agent_browser/`, bounded MCP helper and
   ownership-aware installation/qualification. No UI imports.
3. Connection owner: **all** `connection.dart` and `connection/*.dart`, capability,
   trusted source/session mapping and profile deletion. One editor at a time.
4. UI owner: kit browser pane and kit consent/control parts; screens only compose
   them. UI accesses typed domain/controller state, never shell or socket paths.

All new source files stay below 1,500 lines. Freeze the companion Dart/wire types
before parallel implementation. There is no browser runtime in today's release;
the first browser version requires native Android code and an AndroidX WebKit
dependency, hence a new release. Later Dart-only UI/helper changes may be
Shorebird patches only when the installed native protocol advertises compatible
capabilities. Unknown versions and missing methods stay unavailable. Changing
native security enforcement, view registration, manifest, dependency or assets
always needs another release.

No tests or APK builds run for this docs-only track. Future focused Flutter tests
use the pinned SDK, `--concurrency=1` and `tool/qa/machine_lock.sh`; native and real
WebView claims require instrumented emulator proof on an owner-supplied signed
APK. No physical phone testing is authorized here. Review must resolve B0's two
gates before browser implementation is presented as ready.
