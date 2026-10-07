import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../diagnostics/app_diagnostics.dart';
import '../diagnostics/perf_trace.dart';
import '../diagnostics/report_problem_startup.dart';
import '../platform/app_exit.dart';
import '../state/automation_policy.dart';
import '../state/profiles.dart';
import 'builtin_linux.dart';
import 'builtin_server.dart';
import 'deliberate_stop.dart';
import 'team/builtin_team.dart';

final appLifecycleBridgeProvider = Provider<AppLifecycleBridge>(
  (ref) => AppLifecycleBridge(),
);

/// One per app: the start reads Android's record once, and the shell shows
/// the notice from the same object.
final appExitRecoveryProvider = Provider<AppExitRecovery>((ref) {
  final recovery = AppExitRecovery(
    bridge: ref.watch(appLifecycleBridgeProvider),
  );
  ref.onDispose(recovery.dispose);
  return recovery;
});

/// What the person is told once after Android ended the app while their
/// phone's OpenCode (and maybe the AI Team) ran inside it.
@immutable
class AppExitNotice {
  const AppExitNotice({
    required this.kind,
    required this.at,
    required this.teamStopped,
    this.recoveryAllowed = true,
  });

  final AppExitKind kind;
  final DateTime at;
  final bool teamStopped;

  /// Policy permission for this launch's recovery, not proof of success.
  /// False must never be worded as "starting again" by a notice consumer.
  final bool recoveryAllowed;

  /// A crash is ours to fix, not a phone setting to change.
  bool get offersKeepAlive => kind != AppExitKind.crash;
}

/// On start: learns why the previous process ended and what ran in it,
/// brings back the built-in server (and the AI Team) Android stopped with
/// it, and holds the one-time notice.
///
/// The start itself is [BuiltinServerStarter]'s, the one path every screen
/// uses. Its automatic (healing) restart leaves the team alone, so the team
/// that ran comes back here, once per process ([runOnce]'s `reviveTeam`,
/// BuiltinTeam.ensureRunning). When the
/// app opens on the in-app server the shell already starts it (main.dart's
/// `_autoStartThenConnect`), so this starts it only when another server is
/// selected.
class AppExitRecovery extends ChangeNotifier {
  AppExitRecovery({required this.bridge});

  final AppLifecycleBridge bridge;

  bool _ran = false;
  bool _disposed = false;
  AppExitNotice? _notice;
  AppLaunchReport? _report;

  /// The notice to show, until the person dismisses it.
  AppExitNotice? get notice => _notice;

  /// The last start's report, for diagnostics and tests.
  AppLaunchReport? get report => _report;

  void dismiss() {
    if (_notice == null) return;
    _notice = null;
    _notify();
  }

  /// How long the notice stays once the phone's OpenCode is connected again.
  static const serverBackLinger = Duration(seconds: 15);

  Timer? _serverBackTimer;

  /// The phone's OpenCode is connected again: the notice has said what
  /// happened, so it folds away on its own after [serverBackLinger] instead
  /// of sitting on top of every screen until dismissed (owner report,
  /// 2026-10-07). Keep running stays under In-app Ubuntu. Safe to call on
  /// every rebuild: only the first call after a notice starts the clock.
  void noteServerBack() {
    if (_notice == null || _serverBackTimer != null) return;
    _serverBackTimer = Timer(serverBackLinger, dismiss);
  }

  /// Runs once per app process; later calls do nothing.
  ///
  /// A notable exit is also kept, typed, in the persisted problem report
  /// ([problemReport], by default [ReportProblemStartup.ready]), so it is
  /// still there after the next crash or restart.
  Future<void> runOnce({
    required ProfileStore store,
    required ServerProfile? active,
    required BuiltinServerStarter starter,
    AppDiagnosticsController? diagnostics,
    Future<ReportProblemStartup?>? problemReport,
    Future<void> Function(ServerProfile profile)? recover,
    Future<void> Function(ServerProfile profile)? reviveTeam,
    Future<bool> Function({
      required String profileId,
      required String eventId,
      required DateTime at,
    })?
    onRestart,
  }) async {
    if (_ran) return;
    _ran = true;
    final report = await bridge.launchReport();
    _report = report;
    final crash = report.lastCrash;
    if (crash != null) {
      PerfTrace.mark('crash.last', attrs: crash.traceAttrs);
      // Existing ReportProblemCapture imports early buffered diagnostics and
      // persists this full bounded stack once storage attaches at startup.
      (diagnostics ?? ReportProblemStartup.diagnostics)?.record(
        crash.diagnosticMessage,
        StackTrace.fromString(crash.diagnosticStack),
        source: 'crash.last',
        at: crash.timestamp,
      );
    }
    final stoppedOnPurpose = [
      for (final profile in store.profiles)
        if (looksLikeInAppServer(profile) &&
            DeliberateServerStop.isMarked(store.prefs, profile.id))
          profile,
    ].isNotEmpty;
    final serverWas =
        !stoppedOnPurpose &&
        report.previousServices.contains(BuiltinLinux.serverServiceName);
    // The team runs on the phone's OpenCode: a server stopped on purpose
    // leaves nothing to report or bring back, even if the team outlived it.
    final teamWas =
        !stoppedOnPurpose &&
        report.previousServices.contains(BuiltinTeam.serviceName);
    final wasRunning = serverWas || teamWas;
    final exit = report.exit;
    PerfTrace.mark(
      'app.lastExit',
      attrs: {
        ...?exit?.traceAttrs,
        if (exit == null) 'kind': 'none',
        'server': serverWas,
        'team': teamWas,
      },
    );
    if (exit != null && exit.kind.notable) {
      diagnostics?.record(
        'Android ended the app (${exit.kind.name}): reason ${exit.reason}, '
        'subreason ${exit.subReason}, importance ${exit.importance}'
        '${wasRunning ? ', while ${report.previousServices.join(' and ')} ran' : ''}',
        null,
        source: 'android.exit',
        at: exit.timestamp,
      );
      unawaited(
        (problemReport ?? ReportProblemStartup.ready).then(
          (kept) => kept?.recordAndroidExit(exit),
          onError: (Object _) {},
        ),
      );
    }
    if (!wasRunning) return;
    if (exit != null && exit.kind.notable) {
      final recoveryProfile = looksLikeInAppServer(active)
          ? active
          : _inAppProfile(store, team: teamWas);
      _notice = AppExitNotice(
        kind: exit.kind,
        at: exit.timestamp,
        teamStopped: teamWas,
        recoveryAllowed:
            recoveryProfile != null &&
            AutomationPolicyController.forProfile(
              store.prefs,
              recoveryProfile.id,
            ).value.allows(AutomationBehavior.restartPhoneServer),
      );
      _notify();
    }
    await _restart(
      store: store,
      active: active,
      starter: starter,
      team: teamWas,
      eventId:
          'app-exit:${exit?.timestamp.microsecondsSinceEpoch ?? DateTime.now().microsecondsSinceEpoch}',
      onRestart: onRestart,
      recover: recover,
    );
    if (teamWas && reviveTeam != null) {
      await _reviveTeam(store: store, reviveTeam: reviveTeam);
    }
  }

  /// Brings back the in-app AI Team that ran when the process ended (an
  /// app update, or Android ending the app), on the same terms as the
  /// phone's OpenCode: the profile still has the team on, and the restart
  /// policy allows it. A team the person turned off stays off: its profile
  /// lost the team's config, and [reviveTeam] (BuiltinTeam.ensureRunning)
  /// also reads the durable turned-off mark. A Stop the person gave is
  /// already left out: it cleared the services Android recorded.
  Future<void> _reviveTeam({
    required ProfileStore store,
    required Future<void> Function(ServerProfile profile) reviveTeam,
  }) async {
    final profile = _inAppProfile(store, team: true);
    final wanted =
        profile != null &&
        BuiltinTeam.isBuiltinConfig(profile.orchestration) &&
        AutomationPolicyController.forProfile(
          store.prefs,
          profile.id,
        ).value.allows(AutomationBehavior.restartPhoneServer);
    PerfTrace.mark('app.recover.team', attrs: {'started': wanted});
    if (!wanted) return;
    try {
      await reviveTeam(profile);
    } catch (_) {
      // The team page says it stopped and offers to start it.
    }
  }

  Future<void> _restart({
    required ProfileStore store,
    required ServerProfile? active,
    required BuiltinServerStarter starter,
    required bool team,
    required String eventId,
    Future<void> Function(ServerProfile profile)? recover,
    Future<bool> Function({
      required String profileId,
      required String eventId,
      required DateTime at,
    })?
    onRestart,
  }) async {
    if (recover != null) {
      final profile = looksLikeInAppServer(active)
          ? active
          : _inAppProfile(store, team: team);
      if (profile != null) await recover(profile);
      return;
    }
    if (looksLikeInAppServer(active)) {
      // The shell starts the server it opens on; nothing to add here.
      PerfTrace.mark('app.recover', attrs: {'by': 'shell', 'team': team});
      return;
    }
    final profile = _inAppProfile(store, team: team);
    if (profile == null) {
      PerfTrace.mark('app.recover', attrs: {'by': 'none', 'reason': 'profile'});
      return;
    }
    if (!AutomationPolicyController.forProfile(
      store.prefs,
      profile.id,
    ).value.allows(AutomationBehavior.restartPhoneServer)) {
      return;
    }
    final started = await starter.autoStartIfStopped(
      profile,
      mayStart: () => AutomationPolicyController.forProfile(
        store.prefs,
        profile.id,
      ).value.allows(AutomationBehavior.restartPhoneServer),
    );
    if (started && onRestart != null) {
      await onRestart(
        profileId: profile.id,
        eventId: eventId,
        at: DateTime.now(),
      );
    }
    PerfTrace.mark(
      'app.recover',
      attrs: {
        'by': 'recovery',
        'started': started,
        'team': team && BuiltinTeam.isBuiltinConfig(profile.orchestration),
      },
    );
  }

  /// The saved in-app server; with the team, the one it belongs to.
  static ServerProfile? _inAppProfile(
    ProfileStore store, {
    required bool team,
  }) {
    final candidates = [
      for (final profile in store.profiles)
        if (looksLikeInAppServer(profile)) profile,
    ];
    if (candidates.isEmpty) return null;
    if (team) {
      for (final profile in candidates) {
        if (BuiltinTeam.isBuiltinConfig(profile.orchestration)) return profile;
      }
    }
    return candidates.first;
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _serverBackTimer?.cancel();
    super.dispose();
  }
}
