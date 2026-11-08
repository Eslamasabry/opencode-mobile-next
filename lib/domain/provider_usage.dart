// Plan usage a Paseo daemon reports for the agent providers it runs
// (`provider.usage.list`). Display numbers only: the daemon's free text
// (errors, source and plan labels, details) is never kept, and the two
// names that are kept are cleaned and length-capped.

/// Why a read produced nothing. Carries no daemon text.
enum ProviderUsageFailureKind { unavailable, invalidResponse }

class ProviderUsageFailure implements Exception {
  final ProviderUsageFailureKind kind;
  const ProviderUsageFailure(this.kind);

  @override
  String toString() => 'ProviderUsageFailure(${kind.name})';
}

abstract interface class ProviderUsageGateway {
  /// One bounded read. Throws [ProviderUsageFailure] and nothing else.
  Future<List<ProviderUsageReading>> read();
  Future<void> close();
}

class ProviderUsageWindowReading {
  /// Explicit window identifier (e.g. `weekly`, `5h`, `gemini`).
  final String? windowId;

  /// The window's length when its id names one (`five_hour`, `weekly`).
  final int? durationSeconds;

  /// Cleaned daemon label, shown only when the length is unknown.
  final String label;
  final int remainingPercent;
  final DateTime? resetsAt;
  const ProviderUsageWindowReading({
    this.windowId,
    required this.durationSeconds,
    required this.label,
    required this.remainingPercent,
    required this.resetsAt,
  });
}

class ProviderUsageReading {
  /// Lower-case id such as `claude` or `codex`.
  final String providerId;
  final String displayName;
  final DateTime fetchedAt;
  final List<ProviderUsageWindowReading> windows;

  /// The daemon reported this provider as not signed in: no numbers, only the
  /// way in (the widget offers "Sign in").
  final bool needsSignIn;
  final String? planLabel;
  final String? accountRef;
  const ProviderUsageReading({
    required this.providerId,
    required this.displayName,
    required this.fetchedAt,
    required this.windows,
    this.needsSignIn = false,
    this.planLabel,
    this.accountRef,
  });
}

final _providerIdPattern = RegExp(r'^[a-z0-9][a-z0-9_-]{0,31}$');
bool _isControl(int unit) =>
    unit < 0x20 ||
    (unit >= 0x7f && unit <= 0x9f) ||
    (unit >= 0x200b && unit <= 0x200f) ||
    (unit >= 0x202a && unit <= 0x202e) ||
    (unit >= 0x2066 && unit <= 0x2069);

/// Strips control and direction characters, collapses blanks and caps the
/// length. Null when nothing printable is left.
String? cleanUsageText(Object? value, int maxLength) {
  if (value is! String) return null;
  final text = String.fromCharCodes(
    value.codeUnits.map((u) => _isControl(u) ? 0x20 : u),
  ).replaceAll(RegExp(r'\s+'), ' ').trim();
  if (text.isEmpty) return null;
  final runes = text.runes.toList();
  return runes.length <= maxLength
      ? text
      : String.fromCharCodes(runes.take(maxLength)).trimRight();
}

/// Window lengths the daemon's window ids name.
int? usageWindowSeconds(String id) => switch (id) {
  'five_hour' || '5h' => 5 * 3600,
  'weekly' || 'seven_day' => 7 * 86400,
  _ => null,
};

/// Lower number means shown first when space is short.
int usageWindowPriority(ProviderUsageWindowReading window) =>
    switch (window.durationSeconds) {
      18000 => 0,
      604800 => 1,
      _ => 2,
    };

DateTime? _time(Object? value) {
  if (value is! String || value.length > 64) return null;
  return DateTime.tryParse(value)?.toUtc();
}

/// Whether the daemon's [status] for provider [id] means "sign in to see
/// it". Checked against Paseo's quota fetcher (2026-10-13): Claude with no
/// stored credentials, or one the API refuses (401/403), is reported as
/// `unavailable` with no error; other failures are `error`. Only providers
/// the app signs in through the daemon count, so a provider the person
/// never used does not ask them to sign in.
bool _daemonNeedsSignIn(String id, Object? status) =>
    _daemonSignInProviders.contains(id) && status == 'unavailable';

const _daemonSignInProviders = {'claude', 'antigravity'};

/// Tolerant parse of a `provider.usage.list.response` payload. Unknown
/// providers, statuses and fields are ignored; a provider that is not
/// `available`, or a window without a number, is skipped. Throws
/// [ProviderUsageFailure] only when the payload is not a provider list.
List<ProviderUsageReading> parseProviderUsageList(
  Object? payload, {
  required DateTime now,
  int maxProviders = 16,
  int maxWindows = 3,
}) {
  if (payload is! Map || payload['providers'] is! List) {
    throw const ProviderUsageFailure(ProviderUsageFailureKind.invalidResponse);
  }
  final listFetched = _time(payload['fetchedAt']);
  final readings = <ProviderUsageReading>[];
  final seen = <String>{};
  for (final raw in (payload['providers'] as List).take(maxProviders * 2)) {
    if (raw is! Map) continue;
    final status = raw['status'];
    final id = raw['providerId'];
    if (id is! String || !_providerIdPattern.hasMatch(id)) continue;
    final signedOut = _daemonNeedsSignIn(id, status);
    if (status != 'available' && !signedOut) continue;
    if (!seen.add(id)) continue;
    if (signedOut) {
      readings.add(
        ProviderUsageReading(
          providerId: id,
          displayName: cleanUsageText(raw['displayName'], 24) ?? id,
          fetchedAt: _time(raw['fetchedAt']) ?? listFetched ?? now,
          windows: const [],
          needsSignIn: true,
          accountRef: cleanUsageText(
            raw['account'] ?? raw['email'] ?? raw['accountRef'],
            64,
          ),
        ),
      );
      continue;
    }
    final rawWindows = raw['windows'];
    if (rawWindows is! List) continue;
    final windows = <ProviderUsageWindowReading>[];
    for (final item in rawWindows.take(16)) {
      if (item is! Map) continue;
      final windowId = item['id'];
      final remaining = item['remainingPct'];
      final used = item['usedPct'];
      final double? left = remaining is num
          ? remaining.toDouble()
          : used is num
          ? 100 - used.toDouble()
          : null;
      if (windowId is! String || left == null || !left.isFinite) continue;
      final seconds = usageWindowSeconds(windowId);
      final label = cleanUsageText(item['label'], 24);
      if (seconds == null && label == null) continue;
      windows.add(
        ProviderUsageWindowReading(
          windowId: windowId,
          durationSeconds: seconds,
          label: label ?? '',
          remainingPercent: left.clamp(0, 100).round(),
          resetsAt: _time(item['resetsAt']),
        ),
      );
    }
    if (windows.isEmpty) continue;
    windows.sort(
      (a, b) => usageWindowPriority(a).compareTo(usageWindowPriority(b)),
    );
    var fetched = _time(raw['fetchedAt']) ?? listFetched ?? now;
    if (fetched.isAfter(now)) fetched = now;
    readings.add(
      ProviderUsageReading(
        providerId: id,
        displayName: cleanUsageText(raw['displayName'], 24) ?? id,
        fetchedAt: fetched,
        windows: windows.take(maxWindows).toList(growable: false),
        planLabel: cleanUsageText(raw['planLabel'], 32),
        accountRef: cleanUsageText(
          raw['account'] ?? raw['email'] ?? raw['accountRef'],
          64,
        ),
      ),
    );
    if (readings.length >= maxProviders) break;
  }
  return readings;
}
