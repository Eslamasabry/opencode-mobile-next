import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart';

import '../domain/models.dart' show ProvidersResponse;
import '../domain/agent_account.dart';
import '../domain/provider_quota.dart';
import '../domain/provider_usage.dart';
import '../domain/usage_subscriptions.dart';
import 'notification_preferences.dart';
import 'paseo_usage_monitor.dart';
import 'profiles.dart';
import 'provider_quota_monitor.dart';
import 'usage_subscriptions_controller.dart';

UsageConnectionKind usageConnectionKindFor(
  ServerProfile profile, {
  required bool inApp,
}) => switch (profile.backend) {
  ServerBackend.paseo => UsageConnectionKind.paseoServer,
  ServerBackend.codex => UsageConnectionKind.codexServer,
  ServerBackend.openCode =>
    inApp
        ? UsageConnectionKind.inAppUbuntu
        : UsageConnectionKind.openCodeServer,
};

/// The proper name of a provider the quota readers know.
String usageQuotaName(QuotaProvider provider) => switch (provider) {
  QuotaProvider.codex => 'Codex',
  QuotaProvider.claude => 'Claude',
  QuotaProvider.minimax => 'MiniMax',
  QuotaProvider.glm => 'GLM',
};

/// A reading that says only how the sign-in stands, with no numbers.
UsageReading usageBareReading(
  String connectionId,
  QuotaProvider provider,
  UsageReadingStatus status,
) => UsageReading(
  providerId: provider.name,
  displayName: usageQuotaName(provider),
  connectionId: connectionId,
  status: status,
  fetchedAt: DateTime.now().toUtc(),
  method: SignInMethod.plan,
);

/// The display numbers of a quota snapshot as one connection's reading.
UsageReading usageReadingFromSnapshot(
  String connectionId,
  QuotaProvider provider,
  ProviderQuotaSnapshot snapshot,
) {
  final windows = <UsageWindow>[
    for (final w in snapshot.windows)
      if (w.usedPercent != null)
        UsageWindow(
          id: w.id,
          usedPercent: w.usedPercent!,
          durationSeconds: w.durationSeconds,
          resetsAt: w.resetsAt,
        ),
  ];
  return UsageReading(
    providerId: provider.name,
    displayName: usageQuotaName(provider),
    connectionId: connectionId,
    status: windows.isEmpty
        ? UsageReadingStatus.noLimits
        : UsageReadingStatus.ok,
    fetchedAt: snapshot.fetchedAt,
    windows: windows,
    accountRef: snapshot.account.ref,
    plan: snapshot.account.plan,
    method: SignInMethod.plan,
  );
}

// ---------------------------------------------------------------------------
// The collector: In-app Ubuntu's reader and remote collectors already
// consented to. Also the bridge to the quota monitor's alerts.

class QuotaCollectorSource
    implements UsageSource, UsageAlertBridge, UsageAlertCoverage {
  QuotaCollectorSource({
    required this.profiles,
    required this.isReadable,
    required this.isInApp,
    required this.inAppEnabled,
    required this.createGateway,
    required this.monitor,
    required this.sharedRules,
    this.timeout = const Duration(seconds: 12),
    this.reuseFor = const Duration(minutes: 4),
  });

  final List<ServerProfile> Function() profiles;
  final bool Function(String profileId) isReadable;
  final bool Function(ServerProfile) isInApp;

  /// In-app Ubuntu's reader was set up for this profile and not turned off.
  final bool Function(String profileId) inAppEnabled;
  final ProviderQuotaGateway Function(ServerProfile, QuotaProvider)
  createGateway;
  final ProviderQuotaMonitor monitor;
  final SharedNotifyRules Function() sharedRules;
  final Duration timeout, reuseFor;

  final _snapshots = <String, ProviderQuotaSnapshot>{};

  bool _applies(ServerProfile profile, QuotaProvider provider) {
    if (profile.backend != ServerBackend.openCode ||
        !isReadable(profile.id) ||
        !quotaCollectionAvailable(provider)) {
      return false;
    }
    return isInApp(profile)
        ? inAppEnabled(profile.id)
        : monitor.rulesFor(profile.id, provider) != null;
  }

  @override
  Future<UsageSourceResult> read(UsageReadContext context) async {
    final readings = <UsageReading>[];
    final failures = <UsageFailure>[];
    final unreached = <String>{};
    for (final profile in profiles()) {
      if (unreached.contains(profile.id)) continue;
      for (final provider in QuotaProvider.values) {
        if (!_applies(profile, provider)) continue;
        final id = provider.name;
        ProviderQuotaSnapshot? snapshot;
        if (!context.force) {
          final seen = monitor.observationFor(profile.id, provider);
          final s = seen.snapshot;
          if (seen.status == QuotaMonitorStatus.current &&
              s != null &&
              seen.checkedAt != null &&
              context.now.difference(seen.checkedAt!) < reuseFor) {
            snapshot = s;
          }
        }
        if (snapshot == null) {
          final gateway = createGateway(profile, provider);
          try {
            snapshot = await gateway.readSnapshot().timeout(timeout);
          } on ProviderQuotaFailure catch (failure) {
            switch (failure.kind) {
              case QuotaFailureKind.collectorAuth:
                readings.add(
                  _bare(profile.id, provider, UsageReadingStatus.authRequired),
                );
              case QuotaFailureKind.unavailable:
                if (isInApp(profile)) {
                  failures.add(
                    UsageFailure(profile.id, id, UsageProblem.broken),
                  );
                } else {
                  unreached.add(profile.id);
                }
              case QuotaFailureKind.unsupported ||
                  QuotaFailureKind.invalidResponse:
                failures.add(UsageFailure(profile.id, id, UsageProblem.broken));
            }
            continue;
          } on Object {
            // A timeout or a socket error: the host did not answer.
            if (isInApp(profile)) {
              failures.add(UsageFailure(profile.id, id, UsageProblem.broken));
            } else {
              unreached.add(profile.id);
            }
            continue;
          } finally {
            try {
              gateway.close();
            } on Object {
              // Teardown errors are not shown.
            }
          }
        }
        _snapshots['${profile.id}|$id'] = snapshot;
        switch (snapshot.status) {
          case ProviderQuotaStatus.ok when snapshot.canShowWindows:
            readings.add(_fromSnapshot(profile.id, provider, snapshot));
          case ProviderQuotaStatus.unconfigured:
            final before = context.lastReading?.call(profile.id, id);
            // Only a provider that was signed in here before stays listed.
            if (before != null) {
              readings.add(
                _bare(profile.id, provider, UsageReadingStatus.notSignedIn),
              );
            }
          case ProviderQuotaStatus.authRequired:
            readings.add(
              _bare(profile.id, provider, UsageReadingStatus.authRequired),
            );
          case ProviderQuotaStatus.rateLimited:
            failures.add(
              UsageFailure(profile.id, id, UsageProblem.rateLimited),
            );
          case _:
            failures.add(UsageFailure(profile.id, id, UsageProblem.broken));
        }
      }
    }
    return UsageSourceResult(
      readings: readings,
      failures: failures,
      unreached: unreached,
    );
  }

  UsageReading _bare(
    String connectionId,
    QuotaProvider provider,
    UsageReadingStatus status,
  ) => usageBareReading(connectionId, provider, status);

  UsageReading _fromSnapshot(
    String connectionId,
    QuotaProvider provider,
    ProviderQuotaSnapshot snapshot,
  ) => usageReadingFromSnapshot(connectionId, provider, snapshot);

  // ------------------------------------------------------------- alerts

  /// The quota monitor alerts for a collector reading whose rule exists (or,
  /// for In-app Ubuntu, is created on the next reconcile).
  @override
  bool covers(String connectionId, String providerId) {
    final profile = profiles().where((p) => p.id == connectionId).firstOrNull;
    final provider = QuotaProvider.values
        .where((p) => p.name == providerId)
        .firstOrNull;
    if (profile == null || provider == null) return false;
    return monitor.rulesFor(profile.id, provider) != null ||
        (isInApp(profile) && inAppEnabled(profile.id));
  }

  @override
  List<LegacyQuotaRule> legacyRules() => [
    for (final profile in profiles())
      for (final provider in QuotaProvider.values)
        if (monitor.rulesFor(profile.id, provider) case final rules?)
          LegacyQuotaRule(
            connectionId: profile.id,
            providerId: provider.name,
            accountRef: rules.account,
            threshold: rules.threshold,
          ),
  ];

  @override
  Future<void> reconcile(
    List<QuotaWatch> wanted, {
    required bool alertsOn,
  }) async {
    final all = profiles();
    for (final watch in wanted) {
      final profile = all.where((p) => p.id == watch.connectionId).firstOrNull;
      final provider = QuotaProvider.values
          .where((p) => p.name == watch.providerId)
          .firstOrNull;
      if (profile == null || provider == null) continue;
      final rules = monitor.rulesFor(profile.id, provider);
      if (rules == null) {
        // Only In-app Ubuntu's own reader is enrolled automatically; a remote
        // collector needs the consent that created its rule.
        final snapshot = _snapshots['${profile.id}|${provider.name}'];
        if (!alertsOn ||
            !isInApp(profile) ||
            !inAppEnabled(profile.id) ||
            snapshot == null) {
          continue;
        }
        final shared = sharedRules();
        await monitor.enroll(
          profile.id,
          snapshot,
          notifications: shared.quotaAlerts,
          threshold: watch.threshold,
          wifiOnly: shared.wifiOnly,
          quietStart: shared.quietEnabled ? shared.quietStart : null,
          quietEnd: shared.quietEnabled ? shared.quietEnd : null,
        );
      } else if ((rules.threshold - watch.threshold).abs() > 0.001) {
        await monitor.setPolicy(
          profile.id,
          provider,
          notifications: rules.notifications,
          wifiOnly: rules.wifiOnly,
          threshold: watch.threshold,
          quietStart: rules.quietStart,
          quietEnd: rules.quietEnd,
        );
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Paseo: this phone's agent host and opted-in Paseo servers.

class PaseoUsageSource implements UsageSource {
  PaseoUsageSource({required this.monitor, required this.paseoProfileIds});

  final PaseoUsageMonitor monitor;
  final List<String> Function() paseoProfileIds;

  @override
  Future<UsageSourceResult> read(UsageReadContext context) async {
    await monitor.refresh();
    final readings = <UsageReading>[];
    final unreached = <String>{};
    final ids = [UsageConnection.phoneId, ...paseoProfileIds()];
    for (final id in ids) {
      if (!monitor.enabled(id)) continue;
      final status = monitor.statusFor(id);
      final list = monitor.readingsFor(id);
      // A host that did not answer keeps its cached numbers, marked offline.
      if (list == null ||
          status == PaseoUsageStatus.hostStopped ||
          status == PaseoUsageStatus.unavailable) {
        unreached.add(id);
        continue;
      }
      for (final reading in list) {
        final provider = reading.providerId;
        final accountRef = reading.accountRef;
        if (reading.needsSignIn && reading.windows.isEmpty) {
          readings.add(
            UsageReading(
              providerId: provider,
              displayName: reading.displayName,
              connectionId: id,
              status: UsageReadingStatus.notSignedIn,
              fetchedAt: reading.fetchedAt,
              accountRef: accountRef,
              method: SignInMethod.plan,
            ),
          );
          continue;
        }
        readings.add(
          UsageReading(
            providerId: provider,
            displayName: reading.displayName,
            connectionId: id,
            status: reading.windows.isEmpty
                ? UsageReadingStatus.noLimits
                : UsageReadingStatus.ok,
            fetchedAt: reading.fetchedAt,
            plan: reading.planLabel,
            accountRef: accountRef,
            method: SignInMethod.plan,
            windows: [
              for (final w in reading.windows)
                UsageWindow(
                  id: switch (w.durationSeconds) {
                    18000 => '5h',
                    604800 => 'weekly',
                    _ => w.label.isEmpty ? 'window' : w.label,
                  },
                  usedPercent: 100 - w.remainingPercent.toDouble(),
                  durationSeconds: w.durationSeconds,
                  resetsAt: w.resetsAt,
                  label: w.label,
                ),
            ],
          ),
        );
      }
    }
    return UsageSourceResult(readings: readings, unreached: unreached);
  }
}

// ---------------------------------------------------------------------------
// The Codex account at the connection that is open right now.

class AgentAccountSource implements UsageSource {
  AgentAccountSource({required this.current});

  /// The open connection that supports an agent account, or null. It must not
  /// connect or switch anything.
  final ({UsageConnection connection, AgentAccountGateway gateway})? Function()
  current;

  static const _plans = {
    'free',
    'go',
    'plus',
    'pro',
    'team',
    'business',
    'enterprise',
    'edu',
  };

  @override
  Future<UsageSourceResult> read(UsageReadContext context) async {
    final open = current();
    if (open == null) return const UsageSourceResult();
    final id = open.connection.id;
    final session = open.gateway.openAccountSession();
    try {
      final account = await session.read();
      if (!account.signedIn) {
        final before = context.lastReading?.call(id, 'codex');
        return UsageSourceResult(
          readings: [
            if (before != null || account.requiresSignIn)
              UsageReading(
                providerId: 'codex',
                displayName: 'Codex',
                connectionId: id,
                status: UsageReadingStatus.notSignedIn,
                fetchedAt: context.now.toUtc(),
                method: SignInMethod.plan,
              ),
          ],
        );
      }
      if ((account.type ?? '').toLowerCase().contains('key')) {
        return UsageSourceResult(
          signIns: [
            UsageSignIn(
              providerId: 'codex',
              connectionId: id,
              method: SignInMethod.apiKey,
              displayName: 'OpenAI',
            ),
          ],
        );
      }
      final buckets = await session.rateLimits();
      final windows = <UsageWindow>[];
      if (buckets.isNotEmpty) {
        final bucket = buckets.first;
        for (final (index, w) in [bucket.primary, bucket.secondary].indexed) {
          if (w == null) continue;
          windows.add(
            UsageWindow(
              id: index == 0 ? 'primary' : 'secondary',
              usedPercent: w.usedPercent.toDouble(),
              durationSeconds: w.durationMinutes == null
                  ? null
                  : w.durationMinutes! * 60,
              resetsAt: w.resetsAt,
            ),
          );
        }
      }
      final email = account.email;
      final plan = account.plan?.toLowerCase();
      return UsageSourceResult(
        readings: [
          UsageReading(
            providerId: 'codex',
            displayName: 'Codex',
            connectionId: id,
            status: windows.isEmpty
                ? UsageReadingStatus.noLimits
                : UsageReadingStatus.ok,
            fetchedAt: context.now.toUtc(),
            windows: windows,
            accountRef: email == null || email.isEmpty
                ? null
                : sha256
                      .convert(utf8.encode('codex-account:$email'))
                      .toString(),
            plan: plan != null && _plans.contains(plan) ? plan : null,
            method: SignInMethod.plan,
          ),
        ],
      );
    } on AgentAccountException catch (e) {
      return e.kind == AgentAccountFailure.disconnected
          ? UsageSourceResult(unreached: {id})
          : UsageSourceResult(
              failures: [UsageFailure(id, 'codex', UsageProblem.broken)],
            );
    } on Object {
      return UsageSourceResult(
        failures: [UsageFailure(id, 'codex', UsageProblem.broken)],
      );
    } finally {
      try {
        await session.close();
      } on Object {
        // Teardown errors are not shown.
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Which providers are signed in on each saved OpenCode server.

class OpenCodeSignInSource implements UsageSource {
  OpenCodeSignInSource({
    required this.profiles,
    required this.isReadable,
    required this.providers,
    this.timeout = const Duration(seconds: 10),
  });

  final List<ServerProfile> Function() profiles;
  final bool Function(String profileId) isReadable;

  /// One short-lived read of the server's connected providers. The caller
  /// closes whatever it opened.
  final Future<ProvidersResponse> Function(ServerProfile) providers;
  final Duration timeout;

  @override
  Future<UsageSourceResult> read(UsageReadContext context) async {
    final signIns = <UsageSignIn>[];
    final authoritative = <String>{};
    final unreached = <String>{};
    for (final profile in profiles()) {
      if (profile.backend != ServerBackend.openCode ||
          !isReadable(profile.id) ||
          profile.requiresPasswordReentry) {
        continue;
      }
      try {
        final response = await providers(profile).timeout(timeout);
        for (final p in response.providers) {
          final id = canonicalUsageProviderId(p.id);
          if (id == null) continue;
          signIns.add(
            UsageSignIn(
              providerId: id,
              connectionId: profile.id,
              displayName: _display(id, p.name),
            ),
          );
        }
        authoritative.add(profile.id);
      } on Object {
        unreached.add(profile.id);
      }
    }
    return UsageSourceResult(
      signIns: signIns,
      authoritativeSignIns: authoritative,
      unreached: unreached,
    );
  }

  static String _display(String id, String name) => switch (id) {
    'codex' => 'Codex',
    'glm' => 'GLM',
    'minimax' => 'MiniMax',
    'claude' => 'Claude',
    _ => cleanUsageText(name, 24) ?? id,
  };
}
