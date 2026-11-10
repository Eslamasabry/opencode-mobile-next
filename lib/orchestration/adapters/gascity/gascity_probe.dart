/// Detects a Gas City supervisor behind a URL before a profile enables the
/// plugin: `/.well-known/opencode-mobile-orchestration` first (the host
/// front of tool/host/cp_front answers it with the supervisor URL, the
/// city and whether this device may write), then `/health` for the host
/// shape and version, `/v0/cities` and `/v0/city/<city>/health` for the
/// city, `/v0/city/<city>/status` for the identity shown on the card.
///
/// The transport rule of 04-plugin-architecture §7 is decided here before
/// any network call: `http://` is allowed only to loopback and tailnet
/// addresses (`100.64.0.0/10`, `*.ts.net`); everywhere else it is refused
/// with [ProbePlainHttpRefused]. Reads carry no credential of any kind.
library;

import 'dart:io';

import '../../../domain/orchestration_gateway.dart';
import '../../../domain/loopback_host.dart';
import '../../client/http.dart';
import 'dto/dto.dart';
import 'gascity_mappers.dart';

/// True for a Tailscale address: an IPv4 in the CGNAT range
/// `100.64.0.0/10` (100.64.0.0 – 100.127.255.255), a MagicDNS name ending
/// in `.ts.net`, or a loopback host per [isLoopbackHost]. Brackets around
/// an IPv6 literal are ignored.
bool isTailnetHost(String host) {
  var h = host.trim().toLowerCase();
  if (h.startsWith('[') && h.endsWith(']')) h = h.substring(1, h.length - 1);
  if (h.isEmpty) return false;
  if (isLoopbackHost(h)) return true;
  if (h.endsWith('.ts.net')) return true;
  final address = InternetAddress.tryParse(h);
  if (address == null || address.type != InternetAddressType.IPv4) {
    return false;
  }
  final bytes = address.rawAddress;
  return bytes.length == 4 && bytes[0] == 100 && (bytes[1] & 0xC0) == 0x40;
}

/// Whether the plugin may talk to [url] at all: `https://` anywhere,
/// `http://` only to a loopback or tailnet host. Any other scheme, or a URL
/// without a host, is refused.
bool isOrchestrationUrlAllowed(Uri url) {
  if (!url.hasAuthority || url.host.isEmpty) return false;
  switch (url.scheme) {
    case 'https':
      return true;
    case 'http':
      return isLoopbackHost(url.host) || isTailnetHost(url.host);
    default:
      return false;
  }
}

/// What the probe found behind a URL.
sealed class ProbeVerdict {
  const ProbeVerdict();

  /// Short line for the discovery sheet and logs.
  String describe();
}

/// A running Gas City with the requested (or reported) city.
final class ProbeFound extends ProbeVerdict {
  const ProbeFound({
    required this.host,
    this.version,
    this.city,
    bool? readOnly,
    this.front = false,
    this.identityLogin,
    this.identityAllowed = false,
    this.capabilities = OrchestrationCapabilities.gascityRead,
    this.boundaryTier = '',
  }) : readOnly = readOnly ?? !(front && identityAllowed);

  /// Identity of the host; when [front] is set, [OrchestrationHostIdentity.url]
  /// is the front's `supervisorUrl`, the base every request goes to.
  final OrchestrationHostIdentity host;
  final String? version;
  final String? city;

  /// True unless a host front answered and allows this device to write;
  /// the read adapter alone never writes.
  final bool readOnly;

  /// The well-known document answered with `front: true`.
  final bool front;

  /// The tailnet login the front identified this device as, when any.
  final String? identityLogin;

  /// The front's `identity.allowed`: this device may write through it.
  final bool identityAllowed;

  /// What the adapter built for this host may do: [OrchestrationCapabilities.gascityFront]
  /// behind an allowing front, else [OrchestrationCapabilities.gascityRead].
  final OrchestrationCapabilities capabilities;

  /// The phone engine's proven file boundary (`proot`, `landlock`, or empty
  /// when unknown). Only the phone engine reports one.
  final String boundaryTier;

  @override
  String describe() =>
      'Gas City ${version ?? '?'} city ${city ?? '?'}'
      '${front ? ' via front' : ''}'
      '${readOnly ? ' (read-only)' : ' (controls)'}';
}

/// The front's well-known document (tool/host/cp_front/README.md), decoded
/// leniently: [front] is false for anything that is not the front's shape.
class FrontWellKnown {
  const FrontWellKnown({
    required this.front,
    this.provider,
    this.supervisorUrl,
    this.city,
    this.version,
    this.read = true,
    this.control = false,
    this.merge = false,
    this.identityLogin,
    this.identityAllowed = false,
  });

  /// Null when [json] is not a Gas City front document.
  static FrontWellKnown? fromJson(Map<String, Object?> json) {
    if (readText(json, 'provider') != 'gascity') return null;
    final capabilities = readMapField(json, 'capabilities');
    final identity = readMapField(json, 'identity');
    return FrontWellKnown(
      front: readBool(json, 'front') ?? false,
      provider: readText(json, 'provider'),
      supervisorUrl: readText(json, 'supervisorUrl'),
      city: readText(json, 'city'),
      version: readText(json, 'version'),
      read: readBool(capabilities, 'read') ?? true,
      control: readBool(capabilities, 'control') ?? false,
      merge: readBool(capabilities, 'merge') ?? false,
      identityLogin: readText(identity, 'login'),
      identityAllowed: readBool(identity, 'allowed') ?? false,
    );
  }

  final bool front;
  final String? provider;
  final String? supervisorUrl;
  final String? city;
  final String? version;
  final bool read;
  final bool control;
  final bool merge;
  final String? identityLogin;
  final bool identityAllowed;

  /// Writes are possible: a front that allows this identity to control.
  bool get allowsControl => front && control && identityAllowed;
}

/// The URL answered, but not like a Gas City supervisor (HTML, another
/// service, a `/health` without the expected fields).
final class ProbeNotGasCity extends ProbeVerdict {
  const ProbeNotGasCity({this.statusCode, this.detail});

  final int? statusCode;
  final String? detail;

  @override
  String describe() =>
      'Not a Gas City host'
      '${statusCode == null ? '' : ' (HTTP $statusCode)'}'
      '${detail == null ? '' : ': $detail'}';
}

/// A Gas City answered, but the city is not running (or not registered).
final class ProbeCityNotRunning extends ProbeVerdict {
  const ProbeCityNotRunning({required this.city, this.detail, this.known});

  final String city;
  final String? detail;

  /// Cities the supervisor listed, when `/v0/cities` answered.
  final List<String>? known;

  @override
  String describe() =>
      'City $city is not running${detail == null ? '' : ': $detail'}';
}

/// `http://` to a host that is neither loopback nor tailnet; no request
/// was made.
final class ProbePlainHttpRefused extends ProbeVerdict {
  const ProbePlainHttpRefused({required this.host});

  final String host;

  @override
  String describe() =>
      'Plain http:// is only allowed to localhost or a tailnet address '
      '($host refused)';
}

/// No answer: DNS, refused connection, timeout, TLS failure, bad URL.
final class ProbeUnreachable extends ProbeVerdict {
  const ProbeUnreachable({required this.error, this.url});

  final Object error;

  /// The address the app asked. Dart's refused-connection text ends in
  /// the local (ephemeral) port, not this one ("port = 36580" for a team
  /// on 8472), so the details name the address themselves.
  final String? url;

  @override
  String describe() {
    final asked = _origin(url);
    return asked == null
        ? 'Unreachable: $error'
        : 'Unreachable: $asked did not answer: $error';
  }

  /// Scheme, host and port only: never a user name or password.
  static String? _origin(String? url) {
    final uri = url == null ? null : Uri.tryParse(url);
    if (uri == null || !uri.hasAuthority || uri.host.isEmpty) return null;
    final host = uri.host.contains(':') ? '[${uri.host}]' : uri.host;
    return '${uri.scheme}://$host${uri.hasPort ? ':${uri.port}' : ''}';
  }
}

/// Path of the front's discovery document.
const frontWellKnownPath = '/.well-known/opencode-mobile-orchestration';

/// Probes one URL. Stateless; every call opens and closes its own client.
class GasCityProbe {
  const GasCityProbe({
    this.timeout = const Duration(seconds: 6),
    this.hostMode = OrchestrationHostMode.computer,
  });

  /// Connect and receive timeout for every request the probe makes.
  final Duration timeout;
  final OrchestrationHostMode hostMode;

  /// Probes [url]; [city] is the city the profile wants (null: whatever
  /// `/health` reports). Never throws.
  Future<ProbeVerdict> probe(String url, {String? city}) async {
    final Uri parsed;
    try {
      parsed = Uri.parse(url.trim());
    } on FormatException catch (e) {
      return ProbeUnreachable(error: e);
    }
    if (!parsed.hasAuthority || parsed.host.isEmpty) {
      return ProbeUnreachable(error: FormatException('not a URL', url));
    }
    if (parsed.scheme == 'http' && !isOrchestrationUrlAllowed(parsed)) {
      return ProbePlainHttpRefused(host: parsed.host);
    }
    if (parsed.scheme != 'http' && parsed.scheme != 'https') {
      return ProbeUnreachable(
        error: FormatException('unsupported scheme ${parsed.scheme}', url),
      );
    }

    var client = OrchestrationHttpClient(
      baseUrl: url,
      city: city ?? '',
      connectTimeout: timeout,
      receiveTimeout: timeout,
    );
    try {
      // 0. A host front answers the well-known document; a bare supervisor
      //    (404) or an unidentified peer (403) falls through to /health.
      final wellKnown = await _wellKnown(client);
      if (wellKnown != null) {
        final base = wellKnown.supervisorUrl;
        final parsedBase = base == null ? null : Uri.tryParse(base);
        if (parsedBase != null &&
            parsedBase.hasAuthority &&
            isOrchestrationUrlAllowed(parsedBase) &&
            OrchestrationHttpClient.normalizeBaseUrl(base!) != client.baseUrl) {
          client.close();
          client = OrchestrationHttpClient(
            baseUrl: base,
            city: city ?? '',
            connectTimeout: timeout,
            receiveTimeout: timeout,
          );
        }
      }
      return await _probe(
        client,
        city: city ?? wellKnown?.city,
        wellKnown: wellKnown,
      );
    } finally {
      client.close();
    }
  }

  /// `GET /.well-known/opencode-mobile-orchestration`: the document when
  /// a front answered it, null for any other answer or failure (the plain
  /// supervisor probe decides then).
  Future<FrontWellKnown?> _wellKnown(OrchestrationHttpClient client) async {
    try {
      final json = await client.getJson(frontWellKnownPath);
      final doc = FrontWellKnown.fromJson(json);
      return doc == null || !doc.front ? null : doc;
    } on Object {
      return null;
    }
  }

  Future<ProbeVerdict> _probe(
    OrchestrationHttpClient client, {
    required String? city,
    FrontWellKnown? wellKnown,
  }) async {
    // 1. The supervisor's own health: shape and version.
    final GcHealth health;
    try {
      final json = await client.getJson('/health');
      if (!_looksLikeHealth(json)) {
        return ProbeNotGasCity(
          statusCode: 200,
          detail: 'health answer has no Gas City fields',
        );
      }
      health = GcHealth.fromJson(json);
    } on OrchestrationHttpException catch (e) {
      if (e.isNotFound && e.problem.slug == 'city-not-found') {
        return ProbeCityNotRunning(city: city ?? '', detail: e.problem.message);
      }
      return ProbeNotGasCity(
        statusCode: e.statusCode,
        detail: e.problem.message,
      );
    } on OrchestrationTransportException catch (e) {
      return ProbeUnreachable(error: e.cause ?? e, url: client.baseUrl);
    } on Object catch (e) {
      return ProbeUnreachable(error: e, url: client.baseUrl);
    }

    final wanted = city ?? health.city;
    if (wanted == null || wanted.isEmpty) {
      return ProbeNotGasCity(statusCode: 200, detail: 'health reports no city');
    }
    final scoped = OrchestrationHttpClient(
      baseUrl: client.baseUrl,
      city: wanted,
      connectTimeout: timeout,
      receiveTimeout: timeout,
    );
    try {
      // 2. Is the city registered and running?
      List<String>? known;
      try {
        final cities = GcList<Map<String, Object?>>.fromJson(
          await scoped.getJson('/v0/cities'),
          (json) => json,
        );
        known = [for (final item in cities.items) ?readText(item, 'name')];
        Map<String, Object?>? entry;
        for (final item in cities.items) {
          if (readText(item, 'name') == wanted) {
            entry = item;
            break;
          }
        }
        if (entry == null) {
          return ProbeCityNotRunning(
            city: wanted,
            detail: 'not registered on this supervisor',
            known: known,
          );
        }
        final running = readBool(entry, 'running');
        final status = readText(entry, 'status')?.toLowerCase();
        if (running == false || (running == null && status == 'stopped')) {
          return ProbeCityNotRunning(
            city: wanted,
            detail: status ?? 'not running',
            known: known,
          );
        }
      } on OrchestrationHttpException {
        // Older supervisors may not list cities; the city health decides.
      } on OrchestrationTransportException catch (e) {
        return ProbeUnreachable(error: e.cause ?? e, url: scoped.baseUrl);
      }

      // 3. The city's own health and status (identity).
      GcHealth cityHealth = health;
      GcStatus? status;
      try {
        cityHealth = GcHealth.fromJson(await scoped.getCity('/health'));
      } on OrchestrationHttpException catch (e) {
        if (e.isNotFound) {
          return ProbeCityNotRunning(
            city: wanted,
            detail: e.problem.message,
            known: known,
          );
        }
        return ProbeNotGasCity(
          statusCode: e.statusCode,
          detail: e.problem.message,
        );
      } on OrchestrationTransportException catch (e) {
        return ProbeUnreachable(error: e.cause ?? e, url: scoped.baseUrl);
      }
      try {
        status = GcStatus.fromJson(await scoped.getCity('/status'));
      } on Object {
        // Identity falls back to /health alone.
      }
      final identity = mapHostIdentity(
        url: scoped.baseUrl,
        hostMode: hostMode,
        health: cityHealth.version != null ? cityHealth : health,
        status: status,
      );
      final front = wellKnown != null && wellKnown.front;
      final controls = wellKnown?.allowsControl ?? false;
      return ProbeFound(
        host: identity,
        version: identity.version ?? wellKnown?.version,
        city: identity.city ?? wanted,
        front: front,
        identityLogin: wellKnown?.identityLogin,
        identityAllowed: wellKnown?.identityAllowed ?? false,
        readOnly: !controls,
        capabilities: controls
            ? OrchestrationCapabilities.gascityFront
            : OrchestrationCapabilities.gascityRead,
      );
    } finally {
      scoped.close();
    }
  }

  /// `/health` of a Gas City carries `status` plus `version` or `city`.
  static bool _looksLikeHealth(Map<String, Object?> json) {
    if (readText(json, 'status') == null) return false;
    return readText(json, 'version') != null || readText(json, 'city') != null;
  }
}
