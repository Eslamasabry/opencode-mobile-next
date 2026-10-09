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
import 'phone_agent_work_watch.dart';
import 'phone_server_idle.dart';

/// App-lifetime owner: recovery does not depend on opening This phone.
final phoneServerHealingProvider = Provider<PhoneServerHealing>((ref) {
  final healing = PhoneServerHealing(
    connection: ref.read(connProvider),
    starter: ref.read(builtinServerStarterProvider),
    localAgentWorkBusy: ref.read(connProvider).localPhoneAgentWorkBusy,
    resumeAgentsAfterIdle: ref.read(connProvider).resumePhoneAgentsAfterIdle,
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
    this.localAgentWorkBusy,
    this.resumeAgentsAfterIdle,
    required BuiltinServerRecovery Function(BuiltinRestartRecorder)
    createRecovery,
  }) {
    recovery = createRecovery(_recordRestart);
    report = LifecycleReportController(linux: recovery.linux);
    idle = PhoneServerIdle(
      linux: recovery.linux,
      isForeground: () => !_disposed && _foreground,
      ownerId: () => _owner?.id,
      isReadable: connection.isProfileReadable,
      resumeAgents:
          ({required profileId, required expectedIdleGeneration}) async {
            final resume = resumeAgentsAfterIdle;
            if (resume == null) throw StateError('Idle resume unavailable');
            await resume(
              profileId: profileId,
              expectedIdleGeneration: expectedIdleGeneration,
            );
          },
    );
    agentWork = PhoneAgentWorkWatch(
      hold: (profileId, leaseId, on, hold) =>
          recovery.linux.setPhoneAgentChatWorkLease(
            profileId: profileId,
            leaseId: leaseId,
            on: on,
            hold: hold,
          ),
    );
    recovery.addListener(_recoveryChanged);
    recovery.connectionUnsettled = _connectionUnsettled;
    connection.store.changes.addListener(_syncProfile);
    connection.addListener(_syncProfile);
    connection.addListener(_connectionChanged);
    starter.beforeManualStart = _claimProfile;
    _syncProfile();
    _workHeartbeat = Timer.periodic(
      const Duration(seconds: 15),
      (_) => _observeAgentWork(),
    );
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
  late final PhoneAgentWorkWatch agentWork;
  late final PhoneServerIdle idle;

  final Future<void> Function({
    required String profileId,
    required int expectedIdleGeneration,
  })?
  resumeAgentsAfterIdle;
  Timer? _workHeartbeat;
  Future<void> _workObservations = Future<void>.value();
  int _workObservationRevision = 0;
  int _idleEpoch = 0;
  // A manual claim cancels an old idle restore, but its own legacy launch must
  // still finish. Lifecycle/owner revocations cancel both kinds of work.
  int _launchEpoch = 0;

  bool get idleResuming => idle.running;
  String? get idleResumeFailure => idle.failure;

  /// BA supplies current inventory truth. Absence remains unknown and acquires nothing.
  final bool? Function(String profileId)? localAgentWorkBusy;
  ServerProfile? _owner;
  int _reportedReadyCount = -1;
  bool _foreground = false;
  bool _disposed = false;
  bool _ownerStorageFailed = false;
  bool _manualClaiming = false;
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
    _manualClaiming = true;
    try {
      _invalidateIdle(revokeLaunch: false);
      report.invalidate();
      recovery.setProfile(null);
      await recovery.drainNativeCancellation();
      _ownerStorageFailed = true;
      await BuiltinServerOwner.forPreferences(connection.store.prefs).claim(
        profile.id,
        isReadable: () =>
            !_disposed && connection.isProfileReadable(profile.id),
      );
      _ownerStorageFailed = false;
      _syncProfile();
      recovery.setForeground(_foreground);
      // The native launch snapshots its binding once. Drain migration and binding
      // before sending the recipe, including a check invalidated by this transfer.
      await recovery.check(profile);
      if (recovery.value.profileId != profile.id) await recovery.check(profile);
      if (_disposed ||
          !connection.isProfileReadable(profile.id) ||
          !identical(_owner, profile) ||
          connection.store.prefs.getString(ownerKey) != profile.id) {
        throw StateError('The server profile is unavailable.');
      }
    } finally {
      _manualClaiming = false;
    }
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
      _invalidateIdle();
      _owner = selected;
      report.invalidate();
    }
    recovery.setProfile(selected);
    _observeAgentWork();
    if (_foreground && !_manualClaiming && selected != null) {
      unawaited(check(selected));
    }
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
    _observeAgentWork();
  }

  void _observeAgentWork() {
    if (_disposed) return;
    final revision = ++_workObservationRevision;
    final owner = _owner;
    if (owner == null || !connection.isProfileReadable(owner.id)) {
      agentWork.observe(profileId: null, busy: null);
      return;
    }
    final profileId = connection.store.phoneAgentOwnerId(owner.id);
    bool? busy;
    try {
      // BA resolves the readable server alias into the shared agent owner.
      busy = localAgentWorkBusy?.call(owner.id);
    } catch (_) {}
    agentWork.observe(profileId: profileId, busy: busy);
    // Native idle truth uses the readable server alias. CPU ownership above uses
    // the canonical helper owner; neither alias invents an idle answer.
    _workObservations = _workObservations.then((_) async {
      if (_disposed ||
          revision != _workObservationRevision ||
          !identical(_owner, owner) ||
          !connection.isProfileReadable(owner.id)) {
        return;
      }
      try {
        await recovery.linux.observePhoneAgentWork(
          profileId: owner.id,
          busy: busy,
        );
      } catch (_) {
        // A failed observation expires natively into unknown.
      }
    });
  }

  void setForeground(bool value) {
    _foreground = value;
    if (value) {
      _foregroundWaiter?.complete();
      _foregroundWaiter = null;
    }
    if (!value) {
      _invalidateIdle();
      report.invalidate();
      recovery.setForeground(false);
    } else {
      final owner = _owner;
      if (owner != null) unawaited(check(owner));
    }
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

  void _invalidateIdle({bool revokeLaunch = true}) {
    _idleEpoch++;
    if (revokeLaunch) _launchEpoch++;
    idle.invalidate();
    recovery.setForeground(false);
  }

  bool _idleCurrent(ServerProfile profile, int epoch) =>
      !_disposed &&
      !_manualClaiming &&
      _foreground &&
      epoch == _idleEpoch &&
      identical(_owner, profile) &&
      connection.isProfileReadable(profile.id);

  bool _launchCurrent(ServerProfile profile, int epoch) =>
      !_disposed &&
      !_manualClaiming &&
      _foreground &&
      epoch == _launchEpoch &&
      identical(_owner, profile) &&
      connection.isProfileReadable(profile.id);

  static bool _idleBlocks(BuiltinLinuxStatus status) =>
      status.serverIdlePolicySupported &&
      (!status.serverIdleReceiptValid ||
          status.serverIdleStopped ||
          status.serverIdleHelperStopped ||
          ((status.serverIdleGeneration ?? 0) > 0 && !status.serverRunning));

  static bool _idleBlocksLaunch(BuiltinLinuxStatus status) =>
      _idleBlocks(status) ||
      (status.serverIdlePolicySupported &&
          (status.serverIdleGeneration ?? 0) > 0);

  Future<void> check(ServerProfile profile) async {
    final epoch = _idleEpoch;
    if (!_idleCurrent(profile, epoch)) return;
    await idle.check();
    if (!_idleCurrent(profile, epoch)) return;
    final status = await _status();
    if (!_idleCurrent(profile, epoch)) return;
    if (status == null || idle.failure != null || _idleBlocks(status)) {
      recovery.setForeground(false);
      return;
    }
    recovery.setForeground(true);
    await recovery.check(profile);
  }

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
    if (!_idleCurrent(profile, _idleEpoch)) return;
    final epoch = _idleEpoch;
    final launchEpoch = _launchEpoch;
    final readyBefore = starter.readyCount;
    await check(profile);
    if (!_idleCurrent(profile, epoch)) return;
    // Once per app process. When the healing restart in that check already
    // brought it up and it died since, that is a crash for the healing
    // budget, not a launch.
    if (!_launchStartUsed) {
      _launchStartUsed = true;
      if (starter.readyCount == readyBefore) await _launchStart(profile);
    }
    if (_launchCurrent(profile, launchEpoch)) {
      await connectIfNeeded(profile, automatic: !retry);
    }
  }

  Future<void> _launchStart(ServerProfile profile) async {
    final epoch = _launchEpoch;
    // The recovery owner may have just tried and failed: then the launch
    // start is the one more try.
    var tries = starter.failureFor(profile) == null ? 2 : 1;
    while (tries > 0 && await _launchStartWanted(profile)) {
      if (!_launchCurrent(profile, epoch)) return;
      tries--;
      // A stale server (below) is replaced here too: the explicit start
      // stops the app's own tracked process tree first (BuiltinLinux.kt
      // launchService → removeService), never anything found by name.
      final failure = await starter.start(profile);
      if (!_launchCurrent(profile, epoch)) return;
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
    final epoch = _idleEpoch;
    bool mayAct() =>
        !_disposed &&
        epoch == _idleEpoch &&
        _foreground &&
        connection.isProfileReadable(profile.id) &&
        identical(_owner, profile) &&
        idle.failure == null &&
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
    if (status.serverRecoveryAuthority || _idleBlocksLaunch(status)) {
      return false;
    }
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
      final now = await _status();
      if (now == null ||
          !mayAct() ||
          _idleBlocksLaunch(now) ||
          now.serverRecoveryAuthority ||
          !now.serverRestartWanted) {
        return false;
      }
      if (probe.needsPassword) return true;
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
    _idleEpoch++;
    _launchEpoch++;
    _workObservationRevision++;
    _workHeartbeat?.cancel();
    idle.dispose();
    _foregroundWaiter?.complete();
    _foregroundWaiter = null;
    connection.store.changes.removeListener(_syncProfile);
    connection.removeListener(_syncProfile);
    connection.removeListener(_connectionChanged);
    starter.beforeManualStart = null;
    recovery.removeListener(_recoveryChanged);
    agentWork.dispose();
    recovery.dispose();
    report.dispose();
  }
}
