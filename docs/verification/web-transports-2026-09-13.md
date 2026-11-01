# Web transports

Finish line: a browser build can authenticate to supported OpenCode 1 and 2
servers, discover and load sessions, receive server-sent events as they are
sent (not buffered until the response closes), reconnect, send and abort,
and use PTY tickets through the existing gateways; the AI Team HTTP/SSE
client streams the same way. Non-goal: changing server APIs, bypassing CORS
or mixed-content policy, putting credentials in URLs, or native refactors
beyond what the platform split needs.

Branch `feat/web-transports-20260913`, worktree `oc_app-web-transports`.

## What was wrong

- `dio_web_adapter` 2.2.1 (the adapter `Dio()` selects on the web) issues one
  `XMLHttpRequest` with `responseType = 'arraybuffer'` and completes the
  request from `onLoad`. A `ResponseType.stream` request therefore produced
  no chunk until the server closed the connection: the v1 `/event`, v2
  `/api/event` and session-log streams, and the Gas City event stream, would
  never deliver a live frame in a browser.
- Both PTY channels (`lib/api/product_repository.dart`,
  `lib/api2/gateway_operations.dart`) opened `dart:io` `WebSocket`s, which
  throw `UnsupportedError` on the web.
- `lib/api/server_probe.dart` imported `dart:io` for DNS detection, and its
  "connection refused" verdict would have been shown for any browser failure,
  including a CORS rejection.

## Changes

### `lib/network/` (new)

- `platform_dio.dart`: `configurePlatformDio(Dio)` plus
  `platformDioUsesBrowserTransport` and the pure routing rule
  `platformDioStreamsRequest`. Conditional export: `platform_dio_io.dart`
  (no-op, the `IOHttpClientAdapter` Dio created stays in place),
  `platform_dio_web.dart`, `platform_dio_stub.dart`.
- `platform_dio_web.dart`: `BrowserStreamingHttpClientAdapter`. Requests with
  `ResponseType.stream` go through `fetch()` with an `AbortController`; the
  response body `ReadableStream` is pumped into Dio's `ResponseBody.stream`
  chunk by chunk. Everything else keeps the adapter Dio created (XHR, CORS
  warning silenced). Kept semantics: every `RequestOptions.headers` entry is
  sent (Basic auth stays a header); `connectTimeout` bounds the wait for
  headers as `connectionTimeout`, falling back to `receiveTimeout` as
  `receiveTimeout` when unset; the per-chunk `receiveTimeout` is Dio's own
  `handleResponseStream`, as on native; cancel token, consumer subscription
  cancel, `ResponseBody.close()` and `close(force: true)` all abort the fetch
  and cancel the reader. Failure reasons say what a browser can know.
- `browser_bindings.dart`: the few `dart:js_interop` bindings used (fetch,
  Headers, Response, ReadableStream reader, AbortController, WebSocket,
  MessageEvent, CloseEvent), mirroring `package:web` 1.1.1 shapes. Written
  in-tree so no direct `web` dependency (and no
  `depend_on_referenced_packages` finding) is needed.
- `transport_failure.dart`: `isHostLookupFailure(Object?)` (the
  `SocketException` check moved out of the probe, browser always false),
  `browserNetworkFailureMessage({detail})` and `browserTransportCause`.
- `platform_websocket.dart` / `platform_websocket_contract.dart`:
  `PlatformWebSocket` (text frames as `String`, binary as `Uint8List`,
  `send`, bounded `close`, `done`, close code/reason),
  `connectPlatformWebSocket(uri, timeout)` and the production seam
  `platformWebSocketConnector`. `platform_websocket_io.dart` wraps `dart:io`
  `WebSocket` (connect timeout, close bounded at 5 s);
  `platform_websocket_web.dart` wraps the DOM socket with
  `binaryType = 'arraybuffer'`, treats a close before open as a connection
  failure, and reports the close code because the browser gives nothing
  else. Messages never include the socket URL (it carries the ticket).

### Existing files

- `lib/api/pty_terminal_channel.dart` (new): `PtyTerminalChannel`, the one
  `TerminalChannel` over `PlatformWebSocket`, replacing the two identical
  private `dart:io` channels. Same frame decoding (0x00 meta frame with
  `cursor`, byte-accurate cursor advance), same `write`/`close`.
- `lib/api/product_repository.dart`, `lib/api2/gateway_operations.dart`:
  `connectTerminal` still fetches the single-use ticket with Basic auth and
  the `x-opencode-ticket` CSRF header, then hands the ticket-only `ws(s)://`
  URL to `platformWebSocketConnector`. `dart:io` imports removed.
- `lib/api/opencode_api.dart`, `lib/api2/transport.dart`,
  `lib/orchestration/client/http.dart`, `lib/api/server_probe.dart`: call
  `configurePlatformDio` on the Dio they create (an injected Dio is left to
  its owner; the probe's test adapter still overrides it).
- Truthful browser copy, all with a `browser` parameter defaulting to
  `kIsWeb` so both branches are unit-tested on the VM:
  `explainServerProbeFailure`, `OpenCodeApi.transportCause`,
  `Api2Transport.mapError`, and `OrchestrationTransportException.message`.
  A browser never claims "refused" or "host name not found"; it says the
  server may be down, may not allow this page's origin (CORS), or that an
  https page cannot use an http/ws server. HTTP answers keep their exact
  verdicts.

### Contracts preserved

`ServerGateway`, `ServerOperationsGateway`, `TerminalChannel`,
`EventStreamTransport`, `Api2Transport.openStream`,
`OrchestrationHttpClient.openStream`, all public signatures. Added optional
named parameters only. No persisted schema touched. No generated SDK code
touched. No pubspec, lockfile, web shell, connection controller, chat
library or localization files touched; the new copy is transport error text
in the same hardcoded English as the existing probe strings
(`docs/localization-todo.md` applies).

## Tests added

- `test/network/platform_dio_test.dart`: native keeps the `dart:io` adapter
  instance; each transport configures its own Dio; injected Dio untouched;
  routing rule; host-lookup detection; browser/native failure copy for the
  v1 cause, v2 error mapping and the shared message.
- `test/network/platform_websocket_test.dart`: refused address fails fast
  without the ticket; a server that never upgrades hits the connect timeout;
  endpoint description drops the query.
- `test/network/pty_terminal_channel_test.dart`: frame decoding, cursor
  accounting, meta frames, write/close, broadcast output.
- `test/pty_terminal_connect_test.dart`: OpenCode 1 and 2 gateways obtain
  the ticket (Basic auth + CSRF header on the HTTP call), hand a ticket-only
  `ws://` URL (no password, no `auth_token`) to the connector seam, and wrap
  a platform failure as the existing "Could not connect to the terminal"
  product error.
- `test/server_probe_test.dart`: browser verdicts never claim refused or
  unresolved; timeouts and HTTP answers keep theirs.
- `test/network/support/streaming_test_server.dart`: controllable loopback
  server (SSE hold/release with heartbeats, v2 `/api/event` that closes to
  force reconnect, Gas City stream honouring `Last-Event-ID`, WebSocket
  echo with a meta frame, permissive CORS with preflight). Runs in-process
  or standalone (`dart run …`).
- `test/network/support/streaming_contract.dart` (no `dart:io`): first frame
  observed while the server still holds the response open (a buffering
  transport fails this by name); cancel ends the stream and the server sees
  the disconnect; v2 event stream sends Basic auth and reconnects; AI Team
  SSE resumes with `Last-Event-ID` and sends no credential; platform
  WebSocket text/binary echo and bounded close; server-initiated close code;
  probe succeeds against a v2-shaped server; a closed port is reported
  without an invented diagnosis.
- `test/network/streaming_native_test.dart` (`@TestOn('vm')`): the contract
  on the native transport against the in-process server (native
  preservation).
- `test/network/streaming_browser_test.dart` (`@TestOn('browser')`): the
  contract on the Fetch adapter and DOM socket; skips with instructions
  unless `OC_STREAM_TEST_SERVER` is defined.

## Verification status

Implemented and committed. **Not executed here**: this worktree's
instructions reserve every Flutter/Dart process for the coordinator, so no
analyzer, formatter, VM test or browser test has run on this branch. Please
run, in this order:

```bash
# focused native
flutter test --concurrency=1 \
  test/network/platform_dio_test.dart \
  test/network/platform_websocket_test.dart \
  test/network/pty_terminal_channel_test.dart \
  test/pty_terminal_connect_test.dart \
  test/server_probe_test.dart \
  test/network/streaming_native_test.dart
# native regressions on the touched transports
flutter test --concurrency=1 \
  test/api2_sse_test.dart test/api2_transport_test.dart \
  test/connection_sse_test.dart test/team_gascity_gateway_test.dart \
  test/terminal_accessibility_test.dart
flutter analyze
dart format --set-exit-if-changed lib/network lib/api/pty_terminal_channel.dart test/network test/pty_terminal_connect_test.dart
# browser
dart run test/network/support/streaming_test_server.dart &   # prints OC_STREAM_TEST_SERVER=http://127.0.0.1:<port>
flutter test --platform chrome \
  --dart-define=OC_STREAM_TEST_SERVER=http://127.0.0.1:<port> \
  test/network/streaming_browser_test.dart
flutter build web --release     # compile check of the js_interop code
```

Things a reviewer should look at first, because they were written without
compiler feedback: `lib/network/browser_bindings.dart` (interop shapes),
`platform_dio_web.dart` (`isA`/`as` on JS values, `Function.toJS` for the
`Headers.forEach` callback), and `platform_websocket_web.dart` (event
handler setters).

## Limitations and coordination

- **CORS is the server's decision.** A browser page can only talk to an
  OpenCode server that answers preflights and allows the page's origin, and
  an https page cannot use an http/ws server. The app now says exactly that
  instead of "connection refused"; it does not and must not work around it.
  Whether `opencode serve` / `opencode2 serve` send the needed
  `Access-Control-*` headers is outside this branch; the coordinator's
  browser E2E against a real server will show it.
- The `Accept: text/event-stream` and `Authorization` headers make the
  event-stream request non-simple, so every SSE open costs a preflight;
  `Last-Event-ID` does the same for the AI Team resume. Expected, not a bug.
- `lib/api/mcp_oauth.dart` still uses `dart:io` `HttpServer` for the OAuth
  loopback listener. It compiles for the web but `bind` throws there; the
  integrations screen already catches that and falls back to the manual
  code/URL path, so the behaviour is "no loopback capture in a browser".
  Not changed (outside the transport slice).
- `lib/orchestration/adapters/gascity/gascity_probe.dart` uses
  `InternetAddress.tryParse` (`dart:io`), which throws on the web. That
  adapter is outside this ownership; the AI Team read path over the shared
  client streams correctly, but the host probe needs a platform-neutral IPv4
  check before AI Team can be enabled in a browser. Flagged for the
  adapter owner.
- Codex/A2A transports were not touched (not this ownership).
- No new dependency is required.
