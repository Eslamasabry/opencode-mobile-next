import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/server_probe.dart';
import '../domain/server_gateway.dart' show StreamStatus;
import '../domain/while_away.dart';
import '../state/automation_policy.dart';
import '../state/builtin_server_owner.dart';
import '../state/connection.dart';
import '../state/lifecycle_report.dart';
import '../state/profiles.dart';
import 'builtin_linux.dart';
import 'builtin_server.dart';
import 'builtin_server_recovery.dart';

/// App-lifetime owner: recovery does not depend on opening This phone.
final phoneServerHealingProvider = Provider<PhoneServerHealing>((ref) {
  final healing = PhoneServerHealing(
    connection: ref.read(connProvider),
    starter: ref.read(builtinServerStarterProvider),
    createRecovery: (record) => BuiltinServerRecovery(
      store: ref.read(connProvider).store,
      linux: ref.read(builtinLinuxProvider),
      starter: ref.read(builtinServerStarterProvider),
      onRestart: record,
    ),
  );
  ref.onDispose(healing.dispose);
  return healing;
});

class PhoneServerHealing {
  PhoneServerHealing({
    required this.connection,
    required this.starter,
    required BuiltinServerRecovery Function(BuiltinRestartRecorder)
    createRecovery,
  }) {
    recovery = createRecovery(_recordRestart);
    report = LifecycleReportController(linux: recovery.linux);
    recovery.addListener(_recoveryChanged);
    recovery.connectionUnsettled = _connectionUnsettled;
    connection.store.changes.addListener(_syncProfile);
    connection.addListener(_syncProfile);
    connection.addListener(_connectionChanged);
    starter.beforeManualStart = _claimProfile;
    _syncProfile();
    setForeground(
      WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed,
    );
  }

  /// Shared runtime pointer; deletion clears the reference, not another profile.
  static const ownerKey = BuiltinServerOwner.key;

  final ConnectionController connection;
  final BuiltinServerStarter starter;
  late final BuiltinServerRecovery recovery;
  late final LifecycleReportController report;
  ServerProfile? _owner;
  int _reportedReadyCount = -1;
  bool _foreground = false;
  bool _disposed = false;
  bool _ownerStorageFailed = false;
  String? _lastConnectionAttempt;
  bool _connecting = false;
  DateTime? _connectFailedAt;
  int _connectFailures = 0;
  bool _launchStartUsed = false;
  Completer<void>? _foregroundWaiter;

  /// How long the launch start waits before its one retry.
  @visibleForTesting
  static Duration launchRetryDelay = const Duration(seconds: 2);

  Future<void> _claimProfile(ServerProfile profile) async {
    if (_disposed || !connection.isProfileReadable(profile.id)) {
      throw StateError('The server profile is unavailable.');
    }
    report.invalidate();
    recovery.setProfile(null);
    await recovery.drainNativeCancellation();
    _ownerStorageFailed = true;
    await BuiltinServerOwner.forPreferences(connection.store.prefs).claim(
      profile.id,
      isReadable: () => !_disposed && connection.isProfileReadable(profile.id),
    );
    _ownerStorageFailed = false;
    _syncProfile();
  }

  void _syncProfile() {
    if (_disposed) return;
    final profiles = connection.store.profiles
        .where(looksLikeInAppServer)
        .where((profile) => connection.isProfileReadable(profile.id))
        .toList();
    final owner = connection.store.prefs.getString(ownerKey);
    // A legacy installation with multiple local profiles has no reliable
    // owner. An explicit Start selects it; choosing a remote profile does not.
    final selected = _ownerStorageFailed
        ? null
        : owner == null
        ? (profiles.length == 1 ? profiles.single : null)
        : profiles.where((profile) => profile.id == owner).firstOrNull;
    if (!identical(_owner, selected)) {
      _owner = selected;
      report.invalidate();
    }
    recovery.setProfile(selected);
  }

  /// The app is on this phone's server and its connection is down: the
  /// recovery checks (which reconnect it once the server answers) run at
  /// their quick pace instead of the steady one.
  bool _connectionUnsettled() {
    final owner = _owner;
    return owner != null &&
        connection.profile?.id == owner.id &&
        connection.status != StreamStatus.connected;
  }

  void _connectionChanged() {
    if (!_disposed && _connectionUnsettled()) recovery.expedite();
  }

  void setForeground(bool value) {
    _foreground = value;
    if (value) {
      _foregroundWaiter?.complete();
      _foregroundWaiter = null;
    }
    if (!value) report.invalidate();
    recovery.setForeground(value);
  }

  void _recoveryChanged() {
    final state = recovery.value;
    if (state.phase == BuiltinRecoveryPhase.checking) return;
    if (state.phase != BuiltinRecoveryPhase.ready) {
      report.invalidate();
      return;
    }
    if (!_foreground || _owner == null) return;
    unawaited(connectIfNeeded(_owner!));
    if (_reportedReadyCount != starter.readyCount ||
        report.value.readiness == LifecycleReadiness.unchecked) {
      _reportedReadyCount = starter.readyCount;
      unawaited(report.refresh(_owner));
    }
  }

  Future<bool> _recordRestart({
    required String profileId,
    required String eventId,
    required DateTime at,
  }) async {
    final recorded = await connection.recordServerAct(
      profileId: profileId,
      kind: AutomaticActKind.restart,
      eventId: eventId,
      at: at,
    );
    return recorded;
  }

  Future<void> check(ServerProfile profile) => recovery.check(profile);

  /// The app opened on this phone's own server (main.dart's
  /// `_autoStartThenConnect`): the person is about to use it, so a stopped
  /// server is started for them — once per app process, with one more try
  /// after a fast failure — and then connected.
  ///
  /// This is not crash healing. [recovery]'s durable budget bounds
  /// unattended restarts, and a confirmed automatic restart never gives an
  /// attempt back, so three launches used to exhaust it for good: the fourth
  /// cold start (or one inside the 15/30/45-second retry window) opened on
  /// "stopped" and waited for a tap (docs/qa/slice-qa-autostart-2026-09-28).
  /// Opening the app is the person's own act, like Start, so it goes through
  /// the starter's explicit path (which also resets that budget once the
  /// server answers). Explicit Stop still wins: a stopped intent, the
  /// restart policy being off, a running server or another start in flight
  /// all leave it alone. Foreground only; it never outlives the screen.
  ///
  /// [retry] is the person's own Try again: it connects now instead of
  /// waiting out the automatic reconnect back-off.
  Future<void> startForLaunch(
    ServerProfile profile, {
    bool retry = false,
  }) async {
    if (!_foreground) {
      // Android can report the first resume after the first frame.
      await (_foregroundWaiter ??= Completer<void>()).future;
    }
    if (_disposed) return;
    final readyBefore = starter.readyCount;
    await check(profile);
    if (_disposed) return;
    // Once per app process. When the healing restart in that check already
    // brought it up and it died since, that is a crash for the healing
    // budget, not a launch.
    if (!_launchStartUsed) {
      _launchStartUsed = true;
      if (starter.readyCount == readyBefore) await _launchStart(profile);
    }
    if (!_disposed) await connectIfNeeded(profile, automatic: !retry);
  }

  Future<void> _launchStart(ServerProfile profile) async {
    // The recovery owner may have just tried and failed: then the launch
    // start is the one more try.
    var tries = starter.failureFor(profile) == null ? 2 : 1;
    while (tries > 0 && await _launchStartWanted(profile)) {
      tries--;
      // A stale server (below) is replaced here too: the explicit start
      // stops the app's own tracked process tree first (BuiltinLinux.kt
      // launchService → removeService), never anything found by name.
      final failure = await starter.start(profile);
      if (failure == null) {
        await recovery.check(profile);
        break;
      }
      if (!failure.retryable || tries == 0) break;
      await Future<void>.delayed(launchRetryDelay);
    }
  }

  /// A tracked OpenCode process younger than this may still be booting
  /// (15–40 s on a busy phone or emulator); one this old that still does
  /// not answer our password is stale.
  @visibleForTesting
  static Duration staleAfter = const Duration(seconds: 90);

  /// How long the launch waits on a process whose age native code does not
  /// report (an older APK) before it counts it as stale.
  @visibleForTesting
  static Duration unknownAgeGrace = const Duration(seconds: 30);

  /// How often the launch asks a booting server whether it answers yet.
  @visibleForTesting
  static Duration launchPollInterval = const Duration(seconds: 2);

  Future<bool> _launchStartWanted(ServerProfile profile) async {
    bool mayAct() =>
        !_disposed &&
        _foreground &&
        !starter.starting &&
        connection.profile?.id == profile.id &&
        !connection.hasConnectedServer &&
        looksLikeInAppServer(profile) &&
        AutomationPolicyController.forProfile(
          connection.store.prefs,
          profile.id,
        ).value.allows(AutomationBehavior.restartPhoneServer);
    if (!mayAct()) return false;
    final status = await _status();
    if (status == null) return false;
    // Explicit Stop, notification Stop and the service timeout clear the
    // native intent: the person stopped it, so it stays stopped.
    if (!mayAct() || !status.installed || !status.serverRestartWanted) {
      return false;
    }
    // The native authority owns every unattended dispatch and its budget.
    // Opening the app cannot turn exhaustion or a pending retry into a manual
    // start. A live server that stays unhealthy needs the person's Start.
    if (status.serverRecoveryAuthority) return false;
    if (!status.serverRunning) return true;
    // Running is not answering: a process can hold the port and never
    // accept our password (started with another password, hung while
    // booting). Wait while it is young; replace it once it is stale.
    final deadline = clock.now().add(switch (status.serverUptime) {
      final uptime? when uptime < staleAfter => staleAfter - uptime,
      null => unknownAgeGrace,
      _ => Duration.zero,
    });
    while (true) {
      final probe = await serverProbe(
        baseUrl: profile.baseUrl,
        username: profile.username,
        password: profile.password,
      );
      if (probe.ok || !mayAct()) return false;
      // A server that rejects our password never starts accepting it.
      if (probe.needsPassword) return true;
      final now = await _status();
      if (now == null || !mayAct()) return false;
      if (!now.serverRunning) return now.serverRestartWanted;
      if (!clock.now().isBefore(deadline)) return true;
      await Future<void>.delayed(launchPollInterval);
    }
  }

  Future<BuiltinLinuxStatus?> _status() async {
    try {
      final status = await recovery.linux.status();
      starter.observeStatus(status);
      return status;
    } on BuiltinLinuxException {
      return null;
    }
  }

  /// Delay before an automatic reconnect to a healthy server after a
  /// connect failed; doubles per failure, at most [maxReconnectBackoff].
  @visibleForTesting
  static Duration reconnectBackoff = const Duration(seconds: 10);
  static const maxReconnectBackoff = Duration(seconds: 60);

  /// Opening and confirmed recovery share one reconnect attempt per start.
  Future<void> connectIfNeeded(
    ServerProfile profile, {
    bool automatic = true,
  }) async {
    if (_disposed ||
        !_foreground ||
        connection.profile?.id != profile.id ||
        connection.api != null ||
        connection.hasConnectedServer ||
        starter.failureFor(profile) != null ||
        (automatic &&
            !AutomationPolicyController.forProfile(
              connection.store.prefs,
              profile.id,
            ).value.allows(AutomationBehavior.reconnect))) {
      return;
    }
    if (_connecting) return;
    final attempt = '${profile.id}:${starter.readyCount}';
    if (_lastConnectionAttempt == attempt) {
      // One attempt per start, unless it failed: a server that answers its
      // health check but was too busy for the first connect (a loaded phone
      // right after boot) used to be left on "stopped" for good. The
      // recovery owner's foreground health poll calls back here while it
      // answers, so the retry is bounded by the foreground and backs off.
      final failedAt = _connectFailedAt;
      if (failedAt == null) return;
      if (automatic) {
        final wait =
            reconnectBackoff * (1 << (_connectFailures - 1).clamp(0, 6));
        final capped = wait > maxReconnectBackoff ? maxReconnectBackoff : wait;
        if (clock.now().difference(failedAt) < capped) return;
      }
    } else {
      _connectFailedAt = null;
      _connectFailures = 0;
    }
    _lastConnectionAttempt = attempt;
    _connecting = true;
    try {
      await connection.connect(profile);
    } finally {
      _connecting = false;
    }
    final failed =
        !_disposed &&
        connection.profile?.id == profile.id &&
        !connection.hasConnectedServer &&
        connection.api == null &&
        connection.lastError != null;
    if (failed) {
      _connectFailedAt = clock.now();
      _connectFailures++;
    } else {
      _connectFailedAt = null;
      _connectFailures = 0;
    }
  }

  void dispose() {
    _disposed = true;
    _foregroundWaiter?.complete();
    _foregroundWaiter = null;
    connection.store.changes.removeListener(_syncProfile);
    connection.removeListener(_syncProfile);
    connection.removeListener(_connectionChanged);
    starter.beforeManualStart = null;
    recovery.removeListener(_recoveryChanged);
    recovery.dispose();
    report.dispose();
  }
}
