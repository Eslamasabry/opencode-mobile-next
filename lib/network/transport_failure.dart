/// Truthful descriptions of transport failures across platforms.
///
/// Native sockets tell us *why* a request failed (DNS, refused, TLS). A
/// browser deliberately does not: CORS rejection, mixed content, a typo'd
/// host and a stopped server all arrive as the same generic network error.
/// The helpers here keep the native diagnoses exact and give the browser a
/// message that lists the real possibilities instead of pretending to know.
library;

import 'transport_failure_stub.dart'
    if (dart.library.io) 'transport_failure_io.dart'
    if (dart.library.js_interop) 'transport_failure_web.dart'
    as impl;

/// True when [cause] is a DNS resolution failure (the host name never
/// resolved), as opposed to a reachable host refusing the connection. Always
/// false in a browser, which never reveals the resolver outcome.
bool isHostLookupFailure(Object? cause) => impl.isHostLookupFailure(cause);

/// The sentence shown when a browser request failed before any HTTP answer.
/// The browser's own error text (`TypeError: Failed to fetch`) is the only
/// evidence the platform provides and says nothing a person can act on, so
/// it is not appended: the sentence names the real possibilities instead.
/// Never includes URLs, headers or credentials.
///
/// This exact text is translated at the presentation boundary
/// (`setupUiMessage`, key `browserTransportFailure`), so changing it means
/// changing that entry too.
String browserNetworkFailureMessage() =>
    'The browser could not reach the server. Check the address and that '
    'the server is running. A server on a different origin must allow the '
    'origin of this page (CORS), and a page loaded over https cannot use an '
    'http:// or ws:// server.';

/// Why a Codex or Paseo connection is refused before it starts in a browser.
/// Translated at the same boundary (key `browserAgentSocketUnavailable`), so
/// the text must match that entry.
const browserAgentSocketUnavailableMessage =
    'Codex and Paseo connections are not available in the browser. They sign '
    'in with a WebSocket header that browsers cannot send, and no browser '
    'sign-in is supported yet. Use the phone or desktop app for them; '
    'OpenCode servers work here.';

/// The probe's timeout verdict for a browser, translated at the same
/// boundary (key `browserProbeTimeout`).
const browserProbeTimeoutMessage =
    'The connection timed out. Check the address, and that the server is '
    'reachable from this browser.';
