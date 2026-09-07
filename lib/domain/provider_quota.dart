/// Optional deployment extension, not an upstream OpenCode API route.
const providerQuotaPath = '/ocmn/quota/v1';

enum QuotaProvider { codex, claude, minimax, glm }

/// A parser or historical credential format is not permission to collect data.
/// Claude subscription collection stays off pending a supported integration.
bool quotaCollectionAvailable(QuotaProvider provider) =>
    provider == QuotaProvider.codex ||
    provider == QuotaProvider.minimax ||
    provider == QuotaProvider.glm;

String quotaPathFor(QuotaProvider provider) => switch (provider) {
  QuotaProvider.codex => providerQuotaPath,
  QuotaProvider.claude => '$providerQuotaPath/claude',
  QuotaProvider.minimax => '$providerQuotaPath/minimax',
  QuotaProvider.glm => '$providerQuotaPath/glm',
};

enum ProviderQuotaStatus {
  ok,
  unconfigured,
  unsupported,
  authRequired,
  rateLimited,
  unavailable,
  invalidResponse,
}

enum QuotaAccountStatus { matched, unverified, mismatch, sourceBound }

enum QuotaFreshness { fresh, stale, none }

enum QuotaWindowStatus { reported, missing }

enum QuotaFailureKind {
  collectorAuth,
  unsupported,
  unavailable,
  invalidResponse,
}

/// Contains no raw HTTP error, credential, URL, or provider response.
class ProviderQuotaFailure implements Exception {
  final QuotaFailureKind kind;
  const ProviderQuotaFailure(this.kind);

  @override
  String toString() => 'ProviderQuotaFailure(${kind.name})';
}

abstract interface class ProviderQuotaGateway {
  Future<ProviderQuotaSnapshot> readSnapshot();
  void close();
}

class ProviderQuotaAccount {
  /// An opaque collector-generated reference, never an email or access token.
  /// It is for identity comparisons only, not display.
  final String? ref;
  final QuotaAccountStatus status;
  final String? plan;

  const ProviderQuotaAccount._(this.ref, this.status, this.plan);

  @override
  String toString() => 'ProviderQuotaAccount(${status.name})';
}

class ProviderQuotaWindow {
  final String id;
  final QuotaWindowStatus status;
  final double? usedPercent;
  final int? durationSeconds;
  final DateTime? resetsAt;

  const ProviderQuotaWindow._({
    required this.id,
    required this.status,
    this.usedPercent,
    this.durationSeconds,
    this.resetsAt,
  });

  double? get remainingPercent =>
      usedPercent == null ? null : 100 - usedPercent!;
}

/// Provider windows are separate from OpenCode project consumption and spend.
/// Source-bound snapshots identify the collector's configured credential,
/// not an independently returned account ID.
class ProviderQuotaSnapshot {
  final QuotaProvider provider;
  final ProviderQuotaStatus status;
  final QuotaFreshness freshness;
  final DateTime fetchedAt;
  final DateTime expiresAt;
  final ProviderQuotaAccount account;
  final bool? ordinaryUsageAllowed;
  final List<ProviderQuotaWindow> windows;

  const ProviderQuotaSnapshot._({
    required this.provider,
    required this.status,
    required this.freshness,
    required this.fetchedAt,
    required this.expiresAt,
    required this.account,
    required this.ordinaryUsageAllowed,
    required this.windows,
  });

  bool isStale(DateTime now) =>
      freshness == QuotaFreshness.stale || !now.isBefore(expiresAt);

  bool get canShowWindows =>
      status == ProviderQuotaStatus.ok &&
      (account.status == QuotaAccountStatus.matched ||
          (provider != QuotaProvider.codex &&
              account.status == QuotaAccountStatus.sourceBound));

  factory ProviderQuotaSnapshot.fromJson(Object? value) {
    final json = _object(value);
    final provider = _enum(QuotaProvider.values, json['provider']);
    final source = switch (provider) {
      QuotaProvider.codex => 'codex.wham',
      QuotaProvider.claude => 'claude.oauth',
      QuotaProvider.minimax => 'minimax.tokenPlan',
      QuotaProvider.glm => 'glm.codingPlan',
    };
    if (json['schemaVersion'] != 1 || json['source'] != source) {
      throw const FormatException('Unsupported quota snapshot');
    }
    final status = _enum(ProviderQuotaStatus.values, json['status']);
    final freshness = _enum(QuotaFreshness.values, json['freshness']);
    final fetchedAt = _timestamp(json['fetchedAtMs']);
    final expiresAt = _timestamp(json['expiresAtMs']);
    if (expiresAt.isBefore(fetchedAt)) _invalid();

    final rawAccount = _object(json['account']);
    final accountStatus = _enum(
      QuotaAccountStatus.values,
      rawAccount['status'],
    );
    final ref = rawAccount['ref'];
    if (ref != null &&
        (ref is! String || !RegExp(r'^[a-f0-9]{64}$').hasMatch(ref))) {
      _invalid();
    }
    if ((accountStatus == QuotaAccountStatus.matched ||
            accountStatus == QuotaAccountStatus.sourceBound) &&
        ref == null) {
      _invalid();
    }
    if (accountStatus == QuotaAccountStatus.sourceBound &&
        provider == QuotaProvider.codex) {
      _invalid();
    }
    final plan = rawAccount['plan'];
    const plans = {
      'free',
      'go',
      'plus',
      'pro',
      'team',
      'business',
      'enterprise',
      'edu',
    };
    // Unknown plan names must not become arbitrary provider-controlled copy.
    final safePlan = plan is String && plans.contains(plan) ? plan : null;
    final allowed = json['ordinaryUsageAllowed'];
    if (allowed != null && allowed is! bool) _invalid();
    if (provider != QuotaProvider.codex && allowed != null) _invalid();

    final rawWindows = json['windows'];
    if (rawWindows is! List || rawWindows.length > 64) _invalid();
    final ids = <String>{};
    final windows = <ProviderQuotaWindow>[];
    for (final value in rawWindows) {
      final window = _object(value);
      final id = window['id'];
      if (id is! String ||
          !RegExp(r'^[a-zA-Z0-9._/-]{1,64}$').hasMatch(id) ||
          !ids.add(id)) {
        _invalid();
      }
      final windowStatus = _enum(QuotaWindowStatus.values, window['status']);
      final rawPercent = window['usedPercent'];
      double? percent;
      if (rawPercent != null) {
        if (rawPercent is! num ||
            !rawPercent.isFinite ||
            rawPercent < 0 ||
            rawPercent > 100) {
          _invalid();
        }
        percent = rawPercent.toDouble();
      }
      if ((windowStatus == QuotaWindowStatus.reported) != (percent != null)) {
        _invalid();
      }
      final duration = window['durationSeconds'];
      if (duration != null &&
          (duration is! int || duration <= 0 || duration > 315360000)) {
        _invalid();
      }
      final reset = window['resetsAtMs'];
      windows.add(
        ProviderQuotaWindow._(
          id: id,
          status: windowStatus,
          usedPercent: percent,
          durationSeconds: duration as int?,
          resetsAt: reset == null ? null : _timestamp(reset),
        ),
      );
    }
    // Never display another account's quotas, even if a buggy collector sends
    // measurements with an error status or mismatched identity.
    final attributed =
        accountStatus == QuotaAccountStatus.matched ||
        (provider != QuotaProvider.codex &&
            accountStatus == QuotaAccountStatus.sourceBound);
    if ((status != ProviderQuotaStatus.ok || !attributed) &&
        (windows.isNotEmpty || allowed != null)) {
      _invalid();
    }
    return ProviderQuotaSnapshot._(
      provider: provider,
      status: status,
      freshness: freshness,
      fetchedAt: fetchedAt,
      expiresAt: expiresAt,
      account: ProviderQuotaAccount._(ref as String?, accountStatus, safePlan),
      ordinaryUsageAllowed: allowed as bool?,
      windows: List.unmodifiable(windows),
    );
  }

  @override
  String toString() =>
      'ProviderQuotaSnapshot(${status.name}, ${windows.length} windows)';
}

Map<String, dynamic> _object(Object? value) {
  if (value is! Map<String, dynamic>) _invalid();
  return value;
}

T _enum<T extends Enum>(List<T> values, Object? raw) {
  for (final value in values) {
    if (value.name == raw) return value;
  }
  _invalid();
}

DateTime _timestamp(Object? value) {
  if (value is! int || value < 0 || value > 8640000000000000) _invalid();
  return DateTime.fromMillisecondsSinceEpoch(value, isUtc: true);
}

Never _invalid() => throw const FormatException('Invalid quota snapshot');
