# Agent browser: proposed contract v1, 2026-10-07

Status: **draft for review, not frozen, not implemented or qualified**. Scope and
source evidence are in the [plan](agent-browser-plan-2026-10-07.md). The two B0
prerequisites—trusted launch/session binding and enforceable browser network
policy—are explicit open items. This document defines the public boundary they
must support; it does not assert that today's runtime already supplies them.

## 1. Authority, capabilities and identity

`ServerCapabilities.agentBrowser` is proposed, default `false`. Effective support
requires Android native protocol v1, qualified in-app Ubuntu runtime, qualified
session-bound helper launch, supported isolated WebView profile and qualified
network policy. A saved enable preference is not effective support. Non-Android,
remote OpenCode, external Termux, stale native APKs and unqualified runtimes remain
unavailable. This does not change any existing GenUI types or flags.

The app creates `BrowserScope(profileId, sourceId, sessionId, launchId)` using its
own trusted connection and launch records. IDs are opaque nonempty strings, at
most 128 UTF-8 bytes each. They never come from MCP tool arguments, page content,
directory names or a caller's claimed model/agent name. Native binds a grant to
that scope. `launchId` is random per native agent launch; session deletion or
profile switch invalidates the active lease. The source identity includes the
local runtime instance, not merely a port number.

One active `leaseId` (128 random bits) and one pane exist app-wide in v1. Another
scope receives `busy`, not implicit takeover. A lease is not a secret authority by
itself; every operation also requires an authenticated connection bound to its
scope and grant epoch. Random IDs must never be reused across restart.

The helper is a fixed app-managed stdio program. Install by atomic replacement
and a manifest that identifies exact owned configuration entries; do not replace
user MCP servers with the same name. Qualify and install only supported agents.
Disable revokes first, then removes only owned entries/files. Installer failure
cannot retain an effective grant or block deleting a profile.

Do not pre-allow `mcp__oc-browser__*`. Browser commands can have side effects.
Agent-host permission UX may grant an exact read-only tool set for an active,
person-approved lease in a later measured optimization. No wildcard approval or
the `oc-ui` show exception applies here.

## 2. Local channel v1

### Transport and provisioning

Native creates a private, no-backup runtime directory and a short filesystem
socket path, for example `<app noBackupFilesDir>/ab/<random>/s`. Paths must fit the
platform Unix-socket limit in encoded bytes; reject rather than truncate. Parent
mode is `0700`, socket `0600`. Resolve the Android-owned anchor once; use
no-follow/descriptor-relative checks for descendants and atomic metadata writes.
Do not delete an arbitrary pre-existing socket or follow a symlink on cleanup.

Bind only that runtime directory into the qualified helper's PRoot view, e.g.
`/run/oc-browser`. Do not bind the app's complete files/noBackup directory or
weaken existing confinement. Use a filesystem `LocalSocket` binding and an
explicitly owned listening descriptor, not `LocalServerSocket(String)` (abstract
namespace). There is no TCP listener, HTTP route, network fallback or page-facing
WebSocket for MCP. The [Android API distinction](https://developer.android.com/reference/android/net/LocalServerSocket)
is relevant to both privacy and descriptor cleanup.

Native provisions a 256-bit random capability via a narrowly scoped inherited
descriptor or short-lived private file accessible only to the approved launch.
Pick and qualify the exact provisioning mechanism in B0; do not put it in CLI
arguments, logs, error strings, durable MCP JSON, source fixtures or tool results.
The helper gets a path/descriptor reference, never authority from `profileId`
arguments. Native keeps the expected capability and scope in memory; no grant
survives app-process death. Rotate on re-enable, launch change and takeover return.

PRoot fake users and file modes cannot isolate malicious processes sharing the
real app UID by themselves. Before enabling a runtime, verify that its native
launch/confinement binding prevents another profile/session from reading or using
the provisioned grant. A profile-wide static MCP command is insufficient evidence
of session identity. Unknown launch identity returns `not_qualified`; do not
substitute a session ID reported by the agent.

### Framing and handshake

Duplex Unix stream. Each frame is a 4-byte unsigned big-endian byte length followed
by strict UTF-8 JSON. Request maximum 64 KiB; ordinary response maximum 256 KiB;
screenshot response maximum 2 MiB including base64. Reject zero/oversized lengths,
duplicate keys, malformed UTF-8, nonfinite numbers, excessive depth (>16), unknown
required protocol versions and non-object envelopes before dispatch. No compression,
ancillary descriptors or executable serialization.

Before reading the first frame, native checks kernel peer UID against its own.
This excludes other ordinary applications, not other same-UID agents.
[Peer credentials](https://developer.android.com/reference/android/net/LocalSocket)
are necessary but not sufficient. Authenticate within two seconds:

```json
{"v":1,"kind":"hello","capability":"<secret>","clientNonce":"<128-bit random>"}
```

Successful response:

```json
{"v":1,"kind":"hello_ok","connectionId":"<random>","epoch":1,"maxInFlight":1}
```

Native constant-time compares the capability, resolves the immutable scope from
its grant table, and verifies launch liveness. The nonce prevents accidental
connection reuse; this is local bearer authentication, not a claim of protection
against a same-UID process that can steal the bearer. Failed authentication closes
silently and logs only a bounded reason code. No echo of supplied data. At most
four concurrent sockets, one authenticated connection for the active lease and
ten failed handshakes per minute before backoff.

An authenticated command is:

```json
{"v":1,"kind":"request","id":"r7","seq":7,"epoch":1,"method":"read","params":{"leaseId":"b1","format":"text","maxChars":12000}}
```

`id` is 1–64 ASCII alphanumeric/`_-`; `seq` is a strictly increasing integer below
2^53. Scope is deliberately absent. Responses preserve `id` and `seq`:

```json
{"v":1,"kind":"result","id":"r7","seq":7,"ok":true,"value":{"leaseId":"b1","revision":3,"text":"Example page","truncated":false,"untrusted":true}}
```

Errors use `ok:false,error:{code,retryable,effect}` with app-authored bounded
messages only. `effect` is `none`, `applied` or `unknown`. Epoch and liveness are
checked before queueing, immediately before native execution and before exposing
the result. One operation executes at a time; queue capacity eight. Native control
messages (stop/takeover/lifecycle) bypass the operation queue.

Duplex events are optional hints, not a durable log:

```json
{"v":1,"kind":"event","eventSeq":12,"name":"revoked","leaseId":"b1","reason":"user_stop"}
```

Event names: `state`, `navigation`, `revoked`. No page content is pushed in events.
Maximum 10 events/second, coalesced state updates; revocation is never suppressed.
The helper must treat EOF/timeouts as loss of authority, not wait indefinitely.
Reconnect requires a fresh handshake; `status` returns current state. Commands
are not replayed. The last 32 request IDs may be retained for the life of the
connection to reject duplicates; this is not cross-process exactly-once delivery.

Budget each lease to ten commands/second (burst twenty), one screenshot/second,
and at most one outstanding capture. Avoid storing duplicate PNG/base64 buffers;
release buffers after response or cancellation. Low-memory callbacks may stop the
lease rather than retain a hidden renderer. Page memory is not bounded by the
wire budgets; renderer termination is a supported failure, never a reason to
restart the page automatically.

Maximum command timeout 15 seconds; open/navigation 30 seconds. A person-facing
grant request can wait 60 seconds with cancellation. On timeout after a side effect
started, report `effect:unknown` and require inspection, never automatic retry.
App-side calls do not wait on the model or a daemon to acknowledge revocation.

## 3. MCP tools

MCP negotiation is separate from the private socket protocol. Start qualification
with the repository's `2025-11-25` stdio dialect; advertise only versions actually
covered by runtime fixtures. The current official `2026-07-28` revision introduces
different request/result requirements and is not implicitly supported by copying
the old helper. Pin the implementation target in the qualification evidence.
[2025-11-25 tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools),
[current tools specification](https://modelcontextprotocol.io/specification/2026-07-28/server/tools).

Server name `oc-browser`; tools are exactly `open`, `status`, `read`, `screenshot`,
`console`, `click`, `type`, `scroll`, `close`. The host may decorate names, e.g.
`mcp__oc-browser__open`; wire method names remain undecorated. Unknown tools fail.
Every input object has `additionalProperties:false`. Limits are UTF-8 bytes unless
explicitly character counts. No input accepts scope IDs, tokens, filesystem paths,
raw scripts, XPath, arbitrary CSS selectors, headers, cookies or HTTP bodies.

MCP success uses a text JSON block and `structuredContent` containing the same
object when supported; screenshot also uses the standard MCP image content block
with `mimeType:image/png`. Tool errors have `isError:true`, a safe text code and
structured `{code,retryable,effect}`. JSON-RPC validation errors remain protocol
errors. Public tool annotations are hints, never authorization.

| Tool | Exact inputs (all required unless `?`) | Success output | Availability |
| --- | --- | --- | --- |
| `open` | `url:string<=2048`, `purpose:string<=240`, `interactive?:bool=false` | `{leaseId,state:"ready",revision,url,title,mode:"readOnly"\|"interactive",untrusted:true}` | B1; B2 needed for interactive. Blocks until visible pane and person consent, or returns denied/timeout. Same scope+URL returns existing lease; different destination needs a new navigation grant. |
| `status` | `leaseId?:string` | `{state,leaseId?:string,revision?:int,mode?:string,reason?:code}` | B1; no capture and no new grant. Only caller's scope visible. |
| `read` | `leaseId:string`, `format:"text"\|"dom"`, `maxChars?:int=12000` (1..32000) | `{leaseId,revision,url,title,text?:string,nodes?:Node[],truncated:bool,untrusted:true}` | B1. Snapshot, not live DOM authority. |
| `screenshot` | `leaseId:string` | `{leaseId,revision,width:int,height:int,truncated:false,untrusted:true}` plus one PNG image | B1. Visible viewport only, longest edge <=1280, encoded PNG <=1 MiB; otherwise `too_large`. No full-page capture. |
| `console` | `leaseId:string`, `after?:int=0`, `limit?:int=20` (1..50) | `{leaseId,revision,entries:ConsoleEntry[],next:int,dropped:int,untrusted:true}` | B1. Errors/warnings captured after grant only; does not enable browser debugging. |
| `click` | `leaseId:string`, `revision:int`, `nodeId:string` | `{leaseId,revision,actionId,effect:"applied",untrusted:true}` | B2 interactive lease. Applied means input was dispatched, not that a purchase/save/server operation succeeded. |
| `type` | `leaseId:string`, `revision:int`, `nodeId:string`, `text:string<=4096`, `replace?:bool=true` | Same action result, never echoes `text` | B2. Text-like editable element only, no password/hidden/file fields, no submit or implicit Enter. |
| `scroll` | `leaseId:string`, `revision:int`, `direction:"up"\|"down"`, `pages?:int=1` (1..3) | Same action result | B2. Viewport scroll only; no arbitrary pointer/keyboard synthesis. |
| `close` | `leaseId:string` | `{state:"stopped",effect:"applied"}` | B1. Idempotent for an already closed lease owned by the same scope; cannot close another scope. |

`Node` is `{id:string,role:string,name:string,text?:string,editable:bool,enabled:bool}`.
Maximum 300 nodes, 120 characters per name, 500 per node text, total read cap as
requested. IDs are opaque per-snapshot handles; no DOM selectors escape. Read
only visible main-document content; cross-origin frames and closed shadow roots
return an omission marker, never guessed content. Form values, password fields,
hidden nodes, scripts, styles, cookies, storage, URL credentials/query/fragment
and authentication material are not extracted. Safe URL output contains only
scheme/host/port/path with bounded path length; it is not a navigation token.

`ConsoleEntry` is `{seq:int,level:"warning"|"error",text:string,line?:int}`.
Ring buffer at most 100 entries/64 KiB, each <=1024 characters. No stack locals,
request headers/bodies or source URL query/fragment. Error text itself can contain
secrets; do not claim complete redaction. Console capture is explicitly included
in person consent, returned only on request, never diagnostic logging, and erased
on takeover/stop. A B1 implementation may leave console unavailable until this
capture boundary is qualified, but must say so in capabilities/tools listing.

`revision` increases on navigation, DOM mutation relevant to a snapshot, manual
interaction, viewport change and takeover. Before a mutation, revalidate the node
handle, connection, visibility and revision in one native execution turn. Lost,
hidden, disabled, moved/replaced or stale elements fail, rather than retargeting.
The app never treats a page's claim that an element is safe as authorization.

Host-authored bounded scripts may inspect/operate DOM via native WebView APIs.
Never interpolate raw text into executable source; JSON-encode values and keep
the program fixed. Returned JS objects and element handles are still untrusted;
page monkey-patching cannot cause dispatch of a different native method. A normal
DOM action need not generate a trusted browser user gesture; sites requiring one
return unsupported/require takeover, not a generic automation escape hatch.

Common error codes:

| Code | Meaning / retry rule |
| --- | --- |
| `unavailable`, `not_qualified`, `unsupported_version`, `unsupported_tool` | Effective capability absent; no hidden setup/install. |
| `invalid_input`, `too_large`, `rate_limited`, `queue_full` | Bounds failed before action; `effect:none`. |
| `unauthorized`, `scope_changed`, `expired`, `stopped` | Grant lost; caller cannot restore it itself. |
| `busy`, `consent_required`, `user_denied`, `user_control`, `not_visible` | Person/visibility controls execution; no automatic retry loop. |
| `navigation_blocked`, `tls_error`, `permission_denied`, `unsupported_target` | Policy refusal; do not bypass with another scheme, tool or intent. |
| `stale_revision`, `node_missing`, `node_not_interactable` | New read and explicit agent decision needed. Never silently retarget. |
| `timeout`, `renderer_gone`, `disconnected`, `internal_error` | `effect:unknown` if a mutation started; no mutation replay. Internal exception text stays private. |

## 4. Proposed Dart API consumed by the pane

Domain contracts contain no Flutter `Widget`, WebView controller, native socket,
credential or transport handle. Implementation goes through a single backend
controller. These names are proposed for review; they are not additions to frozen
GenUI interfaces.

```dart
abstract interface class AgentBrowserController implements Listenable {
  BrowserAvailability get availability;
  BrowserPaneState get pane;
  BrowserConsentRequest? get pendingConsent;
  Future<BrowserControlResult> decideConsent(
    String requestId, BrowserConsentDecision decision);
  Future<BrowserControlResult> takeOver(String leaseId);
  Future<BrowserControlResult> returnToAgent(String leaseId);
  Future<BrowserControlResult> stop(String leaseId);
  Future<BrowserControlResult> setPaneVisible(String leaseId, bool visible);
  Future<BrowserControlResult> setEnabled(String profileId, bool enabled);
}
```

All public values are immutable final classes/enums with final properties; lists
are unmodifiable. Results do not throw raw platform exceptions. Proposed fields:

| Type | Properties |
| --- | --- |
| `BrowserAvailability` | `bool supported`, `bool enabled`, `BrowserUnavailableReason? reason`, `Set<BrowserOperation> operations`; reason includes `nativeMissing`, `runtimeUnqualified`, `sessionBindingUnavailable`, `isolatedProfileUnsupported`, `networkPolicyUnqualified`, `restartForCleanup`. |
| `BrowserScope` | `String profileId, sourceId, sessionId, launchId`; internal construction from trusted runtime only. |
| `BrowserPaneState` | `BrowserPhase phase`, `BrowserScope? scope`, `String? leaseId`, `BrowserSurface? surface`, `BrowserPageSummary? page`, `BrowserControlMode? mode`, `int revision`, `BrowserFailure? failure`. |
| `BrowserSurface` | `int nativeViewId`, `String leaseId`, `int generation`; opaque reference for kit platform-view host, unusable for arbitrary native calls. |
| `BrowserPageSummary` | `String safeAddress`, `String title`, `bool loading`; bounded, bidi/control sanitized, never HTML. |
| `BrowserConsentRequest` | `String requestId`, `BrowserScope scope`, `Uri destination`, `String purpose`, `bool interactive`, `DateTime expiresAt`; destination displayed safely, full value never logged. |
| `BrowserConsentDecision` | enum `deny`, `allowReadOnly`, `allowInteractive`; cannot widen origin, scope or expiry. |
| `BrowserControlResult` | `bool accepted`, `BrowserFailure? failure`; accepted means control transition accepted, not external website success. |
| `BrowserFailure` | `BrowserErrorCode code`, `BrowserEffect effect`, `bool retryable`; localized UI copy maps from codes. |
| `BrowserControlMode` | enum `readOnly`, `interactive`, `human`. |
| `BrowserOperation` | enum corresponding exactly to MCP tools. |
| `BrowserEffect` | enum `none`, `applied`, `unknown`. |

The kit pane binds `nativeViewId` to proposed platform view type
`oc/agent_browser/view`. Channel `oc/agent_browser` and event channel
`oc/agent_browser/events` are owned together. Native methods:
`capabilities`, `create`, `decideConsent`, `takeOver`, `returnToAgent`, `stop`,
`setPaneVisible`, `setEnabled`, `deleteProfile`, `snapshot`. Versioned maps are
decoded once into the domain types; the UI never assembles them. Native events
have monotonically increasing generation/event IDs. On a gap, fetch `snapshot`;
never replay an action to repair UI state.

`setPaneVisible(true)` is necessary, not sufficient: native checks attached view,
resumed activity, window focus/visibility and device lock independently. A false
notification always revokes; a true one never regrants. Consent and return-to-agent
arrive only from the trusted app UI, not the MCP channel. Takeover/stop must remain
usable even while a tool is timed out or a renderer is hung.

## 5. State, cancellation and deletion

`BrowserPhase`: `unavailable`, `disabled`, `idle`, `awaitingConsent`, `opening`,
`ready`, `humanControl`, `stopping`, `stopped`, `failed`.

- Effective enablement gives `idle`, with no view or token yet.
- Authenticated `open` gives `awaitingConsent`; denial/expiry returns to `idle`.
- Person approval plus native visibility creates the isolated profile and lease:
  `opening` → `ready`. No background page is created while waiting for consent.
- Takeover immediately advances epoch, rejects queued commands, clears capture
  buffers and enters `humanControl`. The person sees a live page; the agent sees
  only status. Return requires explicit consent and a fresh grant/connection.
- Hidden pane, app pause/lock, profile switch/deletion, session deletion, agent
  launch exit, disconnected controller, capability disable or Stop revoke first:
  `stopping` → `stopped`; destroy view and retire WebView profile. Never auto-resume.
- Renderer loss gives `failed`, revokes, destroys and retires. A new open requires
  consent; do not reload the last URL silently.
- Stop racing with an executing click cannot recall a request already sent to the
  site. Report unknown effect, discard late results and never undo by guessing.

The active grant expires after 15 minutes or five minutes with no commands/person
interaction, whichever comes first. Expiry requires a new explicit grant. A native
visibility heartbeat from Dart is at most two seconds apart; losing it for five
seconds fails closed, but activity/lock callbacks revoke immediately without
waiting for that timeout. No foreground service, boot receiver or background job
keeps the browser executable. App restart starts idle/disabled; live leases and
captured content are never restored.

Persist only `oc.agentBrowser.enabled.<profileId>` plus native owned-profile
retirement metadata, with a schema version. No last URL, DOM, console, form text,
screenshot or token in preferences, crash logs or browser history exports. WebView
may write its own private profile cache/storage while running; privacy copy must
not claim RAM-only/incognito secure erasure. Keep backup exclusions.

Deletion revokes grants and closes sockets synchronously before removing profile
preferences. Destroy associated views and mark owned WebView profiles retired.
Attempt profile deletion where supported; otherwise sweep at next app start
before loading those profiles. Record only the random retired profile names,
bounded to eight; never reuse them or block normal account/profile deletion.
Restart-required cleanup is an availability reason for browser creation, not a
reason to retain the person's account. [ProfileStore deletion constraints](https://developer.android.com/reference/androidx/webkit/ProfileStore).

## 6. Browser security policy and open decisions

Every agent-provided URL must pass the existing `openExternalLink` flow before
first navigation. Reuse its explicit injected `launcher` seam to route an approved
URI into the domain browser request, rather than calling `launchUrl` or native
`loadUrl` directly from UI. The browser layer adds stricter local-origin policy.
This path needs a focused UI-owner test; do not change the universal link rule by
asserting that embedded navigation is exempt.

For later in-page navigation within the approved origin, the original consent is
the bounded navigation grant, subject to native validation of every supported
navigation path. Any new origin pauses and requires a new app-side approval via
the same link gate before it may load. External handoff is a separate human action
using `openExternalLink`, not a fallback for a blocked browser navigation. No
`intent:`, `file:`, `content:`, `javascript:`, custom scheme, embedded userinfo,
opaque URL, control character or malformed address. Internal `about:blank` is
app-authored and used only for clearing the pane.

B1 accepts literal loopback addresses only, with an explicit user-approved dev
port/origin. Never authorize all loopback ports, configured OpenCode/Paseo/control
endpoints, metadata/link-local addresses, private-network ranges or arbitrary
`localhost` aliases. B3 public HTTPS excludes destinations resolving to private,
loopback, link-local or reserved ranges and must recheck redirects/rebinding.
The exact local listener must be qualified as the selected development server;
an agent's port number alone cannot prove ownership.

**Open decision B0-NET:** all resource paths must obey the same network authority,
including frames, fetch, POST, service workers, redirects, WebSockets and WebRTC.
WebView callbacks alone are insufficient. Qualify a supported enforcement design
(potentially a dedicated browser process/proxy with no direct/bypass path) before
enabling this policy. Do not claim a proxy protects UDP or unsupported schemes.
If no complete mechanism fits, return to the owner with a narrowed capability or
revised risk model; do not silently permit unrestricted app-network access.

Explicit native settings/policies regardless of B0-NET:

- Fresh named profile, never default; no personal cookie imports or Android
  account/credential/autofill integration. Disable autofill on the view hierarchy.
- No page-to-native interfaces, debugger/CDP socket, arbitrary script tool or
  native method names in page data. Safe Browsing on; TLS failures cancel.
- File/content/universal-file access off; downloads and file chooser denied;
  geolocation, camera, mic, MIDI, clipboard and notification requests denied.
- No new windows, external activity launches, HTTP auth/client certificate
  prompts or automatic account handoff. JavaScript may run for app pages under
  the network policy; popup/permission prompts never grant authority.
- No mixed HTTP content in B3. B1's explicit HTTP exception covers only its
  approved local dev origin, not the app-wide cleartext policy.
- Screen capture/read/console are withheld during human control. OS-level view
  capture must exclude other app surfaces. No full-display MediaProjection.
- Honor bounded memory/capture budgets and dispose views/listeners on every
  terminal path. Do not use renderer/global timer APIs that disrupt unrelated
  future WebViews without explicit owner review.

## 7. Acceptance matrix and release boundary

Before freezing types, B0 must return exact evidence for: (1) Node/Python helper
to filesystem socket under existing PRoot confinement; (2) real UID and native
launch/session proof; (3) token provisioning/revocation across profile and process
changes; (4) AndroidX feature support/profile storage separation and cleanup;
(5) local-origin enforcement covering the network paths above; (6) visible
platform-view composition, focus, accessibility, keyboard and viewport capture.

Then implement failing-before/passing-after tests for framing/limits, mismatched
scope, denied consent, wrong epochs, stale nodes, idempotent close, uncertain
mutation completion, takeover priority, background revoke, renderer loss,
startup sweep and deletion despite installer failure. Native instrumentation
must prove page scripts cannot call the app and that a profile has no previous
lease's cookies/storage. Real sanitized MCP fixtures and an emulator recording
must show the visible pane, tool response, immediate takeover and stop.

This contract does not add permission to build an APK or touch the physical
phone. Initial implementation requires a new signed native release supplied by
the owner. Later Dart patches may use only capabilities already supported and
qualified in that exact native protocol. Protocol mismatch, missing feature or
unknown runtime always returns unavailable; never optimistic capability true.
