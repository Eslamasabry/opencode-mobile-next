/// Server pairing: the payload `opencode2 pair` prints, and the rule that
/// picks which of its addresses this device should actually dial.
///
/// `opencode2 pair` renders a QR whose contents are exactly
/// `JSON.stringify({ urls, username: "opencode", password })` — verified
/// against the CLI's own `cli.pair` handler and by decoding a live QR:
///
/// ```json
/// {"urls":["http://127.0.0.1:49374"],"username":"opencode","password":"…"}
/// ```
///
/// `urls` is an array because the service reports every address it is bound
/// to: loopback always, plus a LAN address once the operator has run
/// `opencode service set hostname 0.0.0.0`. The CLI itself prints
/// `urls[0] ?? "(none)"`, so an empty array is a shape the server can emit
/// and this parser must survive.
///
/// The payload is a credential. Nothing here logs it, and
/// [PairingPayload.toString] redacts it so an accidental interpolation into
/// an error string, a `debugPrint`, or a crash report cannot leak it.
library;

import 'dart:convert';

import '../api/server_probe.dart';
import '../platform/platform_capabilities.dart';
import 'profiles.dart';

/// Ceiling on an accepted pairing payload, in UTF-16 code units.
///
/// A real payload is ~150 bytes; even a service bound to a dozen interfaces
/// stays under 1 KB. The cap exists so a scanner pointed at a QR carrying a
/// megabyte of text — or a clipboard holding an entire file — is rejected on
/// sight rather than handed to `jsonDecode`.
const int maxPairingPayloadLength = 8192;

/// Ceiling on the number of addresses a payload may carry. A host with more
/// than this many bound interfaces is not a shape we probe one-by-one.
const int maxPairingUrls = 32;

/// The username the v2 server always uses; the payload states it explicitly,
/// but a payload that omits it still means this.
const String pairingDefaultUsername = 'opencode';

/// A parsed `opencode2 pair` payload.
///
/// The password is held behind [consume] rather than in a plain final field.
/// Dart strings are immutable, so nothing can scrub the characters
/// themselves out of the heap — but dropping the only reference this object
/// holds means that once the credential has been handed to the profile
/// editor, a payload object still sitting in a closure, a route argument, or
/// a captured stack frame no longer yields it. That is the real guarantee on
/// offer, and it is worth having; anything stronger would be a claim the
/// language cannot back.
class PairingPayload {
  PairingPayload({
    required List<String> urls,
    required this.username,
    required String password,
  }) : urls = List.unmodifiable(urls),
       _password = password;

  /// Every address the server reported, in the order the server reported it.
  final List<String> urls;

  /// Always `opencode` in practice; carried through rather than assumed so a
  /// future server that changes it keeps working.
  final String username;

  String? _password;

  /// True once [consume] has run and this object no longer holds the secret.
  bool get isConsumed => _password == null;

  /// The serve password. Throws after [consume] — reading a consumed payload
  /// is a bug, and failing loudly beats silently pairing with an empty
  /// password.
  String get password {
    final value = _password;
    if (value == null) {
      throw StateError(
        'This pairing payload was already consumed. Parse the code again.',
      );
    }
    return value;
  }

  /// Drops the password reference. Call as soon as the credential has been
  /// written into the field or profile that owns it.
  void consume() => _password = null;

  /// Redacted on purpose: see the class comment. `urls` and `username` are
  /// not secret and are the two things worth seeing in a diagnostic.
  @override
  String toString() =>
      'PairingPayload(urls: $urls, username: $username, '
      'password: ${isConsumed ? '<consumed>' : '<redacted>'})';
}

/// The outcome of [parsePairingPayload]: exactly one of [payload] or
/// [error] is non-null.
class PairingParseResult {
  const PairingParseResult.success(PairingPayload this.payload) : error = null;
  const PairingParseResult.failure(String this.error) : payload = null;

  final PairingPayload? payload;

  /// A product-facing sentence. Never contains any part of the input — a
  /// malformed payload may still be a real password with a stray character,
  /// and echoing the input back into a snackbar would put it on screen and
  /// into the widget tree.
  final String? error;

  bool get ok => payload != null;
}

/// A cheap, allocation-free sniff for "this text is trying to be a pairing
/// code", used to decide whether to offer the pairing path at all.
///
/// Deliberately loose: it must say yes to payloads this parser will then
/// reject with a specific reason, because "that is a pairing code, and here
/// is what is wrong with it" is a better answer than silence. It must say no
/// to an ordinary URL or a pasted password, which are the two things people
/// actually put in these fields.
bool looksLikePairingPayload(String raw) {
  final trimmed = raw.trim();
  if (trimmed.length < 2 || trimmed.length > maxPairingPayloadLength) {
    return false;
  }
  if (!trimmed.startsWith('{') || !trimmed.endsWith('}')) return false;
  return trimmed.contains('"urls"') || trimmed.contains("'urls'");
}

/// Parses the JSON `opencode2 pair` encodes into its QR.
///
/// Every failure is a sentence the user can act on, and none of them quote
/// the input. Handles, in order: empty input, oversized input, non-JSON,
/// JSON that is not an object, a missing or wrongly-typed `urls`, an empty
/// `urls`, too many urls, entries that are not usable strings, a
/// wrongly-typed `username`, and a missing or wrongly-typed `password`.
PairingParseResult parsePairingPayload(String raw) {
  final trimmed = raw.trim();
  if (trimmed.isEmpty) {
    return const PairingParseResult.failure(
      'There is no pairing code here. Run `opencode2 pair` on the server and '
      'scan or copy what it prints.',
    );
  }
  if (trimmed.length > maxPairingPayloadLength) {
    return const PairingParseResult.failure(
      'That is far too long to be a pairing code. Copy only the line '
      '`opencode2 pair` prints, or scan its QR code.',
    );
  }
  final Object? decoded;
  try {
    decoded = jsonDecode(trimmed);
  } on FormatException {
    return const PairingParseResult.failure(
      'That is not a pairing code. Run `opencode2 pair` on the server and '
      'scan or copy what it prints.',
    );
  }
  if (decoded is! Map) {
    return const PairingParseResult.failure(
      'That pairing code is the wrong shape — it should be a JSON object '
      'with `urls`, `username`, and `password`.',
    );
  }

  if (!decoded.containsKey('urls')) {
    return const PairingParseResult.failure(
      'That pairing code has no `urls` field, so there is no address to '
      'connect to.',
    );
  }
  final rawUrls = decoded['urls'];
  if (rawUrls is! List) {
    return const PairingParseResult.failure(
      'That pairing code\'s `urls` field is not a list of addresses.',
    );
  }
  if (rawUrls.length > maxPairingUrls) {
    return const PairingParseResult.failure(
      'That pairing code lists more addresses than this app will try. '
      'Bind the server to one interface and pair again.',
    );
  }
  final urls = <String>[];
  for (final entry in rawUrls) {
    if (entry is! String) {
      return const PairingParseResult.failure(
        'That pairing code lists an address that is not text.',
      );
    }
    final url = entry.trim();
    if (url.isEmpty) continue;
    if (url.length > 2048) {
      return const PairingParseResult.failure(
        'That pairing code lists an address far too long to be a server URL.',
      );
    }
    if (!urls.contains(url)) urls.add(url);
  }
  if (urls.isEmpty) {
    // The CLI prints `urls[0] ?? "(none)"`, so this is a shape the server
    // really can emit — a service that reported no bound address. Say what
    // to do about it rather than "invalid payload".
    return const PairingParseResult.failure(
      'That pairing code carries no server address. Check that the server is '
      'actually listening, then run `opencode2 pair` again.',
    );
  }

  final rawUsername = decoded['username'];
  if (rawUsername != null && rawUsername is! String) {
    return const PairingParseResult.failure(
      'That pairing code\'s `username` field is not text.',
    );
  }
  final username = (rawUsername as String?)?.trim();

  if (!decoded.containsKey('password')) {
    return const PairingParseResult.failure(
      'That pairing code has no `password` field. It may have been truncated '
      '— scan or copy the whole code.',
    );
  }
  final rawPassword = decoded['password'];
  if (rawPassword is! String) {
    return const PairingParseResult.failure(
      'That pairing code\'s `password` field is not text.',
    );
  }

  return PairingParseResult.success(
    PairingPayload(
      urls: urls,
      username: (username == null || username.isEmpty)
          ? pairingDefaultUsername
          : username,
      password: rawPassword,
    ),
  );
}

/// What happened to one address from a pairing payload.
class PairingUrlOutcome {
  const PairingUrlOutcome({
    required this.url,
    required this.probed,
    required this.reason,
    this.result,
  });

  /// The address as this app would use it — normalized, not necessarily the
  /// exact string the server printed.
  final String url;

  /// False when the address was ruled out before any request was made
  /// (malformed, or cleartext HTTP off this device).
  final bool probed;

  /// Why this address was not chosen, as a sentence. Null for the winner.
  final String? reason;

  /// The probe verdict, when one was taken.
  final ServerProbeResult? result;

  /// True when an OpenCode server answered at this address, whether or not
  /// it accepted the credentials. A protocol-shaped answer — 2xx health,
  /// 401, 503 — is what separates "the server is there" from "nothing is
  /// listening", and it is the distinction the user needs.
  bool get answered =>
      result != null &&
      (result!.ok || result!.flavor != ServerFlavor.unknown);
}

/// The result of trying every address in a pairing payload.
class PairingSelection {
  const PairingSelection({
    required this.outcomes,
    this.chosenUrl,
    this.chosenResult,
  });

  final List<PairingUrlOutcome> outcomes;

  /// The address that answered, or null when none did.
  final String? chosenUrl;

  /// The chosen address's probe verdict. May itself be a failure — a server
  /// that answers 401 because the payload's password is stale is still the
  /// right host, and saying "password rejected" beats "could not connect".
  final ServerProbeResult? chosenResult;

  bool get ok => chosenUrl != null;

  /// True when a server answered *and* accepted the pairing credentials.
  bool get connected => chosenResult?.ok ?? false;

  /// A per-address explanation of why nothing worked.
  ///
  /// Generic failure text is useless here: whether to run `adb reverse` or to
  /// bind the service to the network depends entirely on *which* address
  /// failed and how. So each address gets its own line.
  String get failureDetail => outcomes
      .map((o) => '${o.url} — ${o.reason ?? 'Did not answer.'}')
      .join('\n');
}

/// Orders a payload's addresses by how likely this device is to reach them.
///
/// On a phone, the server's own `127.0.0.1` is not the phone's `127.0.0.1`:
/// it is reachable only when the user has bridged it with `adb reverse` or an
/// SSH forward. A routable address is the better first guess. On desktop the
/// opposite holds — the server is almost always the same machine, and
/// loopback is both the fastest and the only address guaranteed not to be
/// firewalled.
///
/// Ordering only decides who wins when several addresses answer; the probe
/// remains the arbiter, and every candidate is still tried.
List<String> orderPairingCandidates(
  Iterable<String> urls, {
  required bool preferLoopback,
}) {
  final loopback = <String>[];
  final routable = <String>[];
  for (final url in urls) {
    final host = Uri.tryParse(url)?.host ?? '';
    (isLoopbackHost(host) ? loopback : routable).add(url);
  }
  return preferLoopback
      ? [...loopback, ...routable]
      : [...routable, ...loopback];
}

/// Probes a pairing payload's addresses and picks the one to connect to.
///
/// Addresses are first put through the app's existing transport policy
/// ([validateServerProfileUrl]): a pairing code is not a licence to relax it.
/// `opencode2 pair` emits `http://` URLs, and once the operator runs
/// `opencode service set hostname 0.0.0.0` one of them is a cleartext LAN
/// address. Dialing that would put HTTP Basic — the serve password, base64 of
/// nothing — across the network in the clear, and Android's own
/// `network_security_config.xml` would block it a layer lower anyway. Such an
/// address is reported with the reason it was skipped rather than silently
/// dropped, because "your server is only reachable in the clear" is exactly
/// what the user needs to know.
///
/// Surviving candidates are probed in [orderPairingCandidates] order. The
/// first that connects wins and probing stops. If none connect but one
/// *answered* — a 401 from a stale password, a 503 from a booting server —
/// that address is chosen anyway with its honest verdict, because it is
/// unambiguously the right host.
Future<PairingSelection> selectPairingUrl(
  PairingPayload payload, {
  ServerProbe? probe,
  bool? preferLoopback,
}) async {
  final runProbe = probe ?? serverProbe;
  final preferLocal = preferLoopback ?? platformCapabilities.isDesktop;
  final password = payload.password;
  final username = payload.username;

  final outcomes = <PairingUrlOutcome>[];
  final candidates = <String>[];
  for (final raw in payload.urls) {
    final url = normalizeServerProfileUrl(raw);
    final invalid = validateServerProfileUrl(
      url,
      username: username,
      password: password,
    );
    if (invalid != null) {
      outcomes.add(
        PairingUrlOutcome(url: url, probed: false, reason: invalid),
      );
      continue;
    }
    candidates.add(url);
  }

  for (final url in orderPairingCandidates(
    candidates,
    preferLoopback: preferLocal,
  )) {
    final result = await runProbe(
      baseUrl: url,
      username: username,
      password: password,
    );
    if (result.ok) {
      outcomes.add(
        PairingUrlOutcome(
          url: url,
          probed: true,
          reason: null,
          result: result,
        ),
      );
      return PairingSelection(
        outcomes: outcomes,
        chosenUrl: url,
        chosenResult: result,
      );
    }
    outcomes.add(
      PairingUrlOutcome(
        url: url,
        probed: true,
        reason: result.message,
        result: result,
      ),
    );
  }

  // Nothing connected. An address that answered is still the right host.
  for (final outcome in outcomes) {
    if (outcome.answered) {
      return PairingSelection(
        outcomes: outcomes,
        chosenUrl: outcome.url,
        chosenResult: outcome.result,
      );
    }
  }
  return PairingSelection(outcomes: outcomes);
}
