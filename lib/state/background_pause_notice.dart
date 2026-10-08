import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../diagnostics/device_diagnostics_gateway.dart';
import '../domain/app_diagnostics_gateway.dart';
import 'connection.dart' show connProvider;

/// Where the app-wide "background connection paused" notice is (FD3).
enum BackgroundPauseNoticePhase {
  /// Android stopped the background connection; Resume is offered.
  paused,

  /// The person tapped Resume; Android has not confirmed the start yet.
  resuming,

  /// The last Resume was not confirmed; the pause is still in effect.
  failed,
}

/// What the notice says right now: one durable pause, read from the device
/// diagnostics gateway, plus this notice's own Resume state.
@immutable
class BackgroundPauseNoticeState {
  const BackgroundPauseNoticeState({
    required this.reason,
    required this.at,
    required this.canResume,
    required this.phase,
    this.resumingSince,
  });

  final BackgroundPauseReason reason;

  /// UTC. The stop time for a time limit or a user stop; the observation
  /// time for a restriction or an unexplained stop (contract FD3).
  final DateTime at;
  final bool canResume;
  final BackgroundPauseNoticePhase phase;

  /// When the current Resume tap began, while [phase] is resuming.
  final DateTime? resumingSince;
}

/// The one owner of the pause notice: which pause it is about, whether the
/// person hid it, and the Resume tap in flight. The pause itself stays in
/// the background service's durable receipt; this never invents or clears
/// one. Only a confirmed start (the gateway's state turning active) makes
/// the notice go away by itself.
class BackgroundPauseNotice extends ChangeNotifier with WidgetsBindingObserver {
  BackgroundPauseNotice({
    required AppDiagnosticsGateway gateway,
    required SharedPreferences preferences,
    DateTime Function()? clock,
    bool refreshOnForeground = true,
  }) : _gateway = gateway,
       _preferences = preferences,
       _clock = clock ?? DateTime.now {
    _dismissed = _readDismissed();
    _gateway.addListener(_changed);
    if (refreshOnForeground) {
      WidgetsBinding.instance.addObserver(this);
      _observing = true;
    }
  }

  /// App-global, not profile data: the pause receipt it refers to is one per
  /// phone. Holds the id of the last pause the person hid.
  static const dismissedKey = 'oc.backgroundPauseDismissed';

  final AppDiagnosticsGateway _gateway;
  final SharedPreferences _preferences;
  final DateTime Function() _clock;
  bool _observing = false;
  String? _dismissed;
  String? _failedFor;
  Future<bool>? _inflight;
  DateTime? _resumingSince;
  bool _disposed = false;

  /// The same pause keeps one id across restarts: its reason and time.
  static String? idOf(BackgroundPauseState pause) {
    final at = pause.at;
    if (!pause.supported || !pause.paused || at == null) return null;
    return '${pause.reason.name}@${at.millisecondsSinceEpoch}';
  }

  /// Null when there is nothing to show: no pause, an unsupported platform,
  /// or this exact pause was hidden.
  BackgroundPauseNoticeState? get notice {
    final pause = _gateway.backgroundPause;
    final id = idOf(pause);
    if (id == null || id == _dismissed) return null;
    final resuming = _inflight != null;
    return BackgroundPauseNoticeState(
      reason: pause.reason,
      at: pause.at!,
      canResume: pause.canResume,
      phase: resuming
          ? BackgroundPauseNoticePhase.resuming
          : id == _failedFor
          ? BackgroundPauseNoticePhase.failed
          : BackgroundPauseNoticePhase.paused,
      resumingSince: resuming ? _resumingSince : null,
    );
  }

  /// Only from a visible Resume tap. A second tap while one is in flight
  /// joins it instead of asking Android twice. True once Android confirmed
  /// the background connection runs again.
  Future<bool> resume() {
    final inflight = _inflight;
    if (inflight != null) return inflight;
    if (notice == null) return Future.value(false);
    final future = _resume();
    _inflight = future;
    _resumingSince = _clock();
    notifyListeners();
    return future;
  }

  Future<bool> _resume() async {
    // Run the platform call after the in-flight state is published.
    await Future<void>.value();
    final id = idOf(_gateway.backgroundPause);
    BackgroundResumeResult result;
    try {
      result = await _gateway.resumeBackground();
    } catch (_) {
      result = BackgroundResumeResult(
        _gateway.backgroundPause,
        error: DiagnosticsError.resumeFailed,
      );
    }
    final confirmed =
        result.error == null && result.state.active && !result.state.paused;
    // Busy means another start (a permission prompt) is already deciding;
    // it is not this tap's failure, so the plain Resume stays.
    if (!confirmed && result.error != DiagnosticsError.busy) {
      _failedFor = id;
    } else if (confirmed) {
      _failedFor = null;
    }
    _inflight = null;
    _resumingSince = null;
    if (!_disposed) notifyListeners();
    return confirmed;
  }

  /// Hides this pause for good; a later, different pause shows again.
  void dismiss() {
    final id = idOf(_gateway.backgroundPause);
    if (id == null || _inflight != null) return;
    _dismissed = id;
    _failedFor = null;
    unawaited(_preferences.setString(dismissedKey, id));
    notifyListeners();
  }

  /// Re-reads the service's receipt (on return to the app).
  Future<void> refresh() async {
    if (_inflight != null) return;
    try {
      await _gateway.refreshBackgroundPause();
    } catch (_) {
      // The gateway reports failures as state; nothing to show here.
    }
  }

  /// A plain observer, not an AppLifecycleListener: it accepts any order of
  /// states (tests and some OEMs skip the intermediate ones).
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(refresh());
  }

  String? _readDismissed() {
    try {
      return _preferences.getString(dismissedKey);
    } catch (_) {
      return null;
    }
  }

  void _changed() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _gateway.removeListener(_changed);
    if (_observing) WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}

/// One per app, on the device diagnostics gateway the main connection owns.
final backgroundPauseNoticeProvider = Provider<BackgroundPauseNotice>((ref) {
  final notice = BackgroundPauseNotice(
    gateway: ref.watch(deviceDiagnosticsGatewayProvider),
    preferences: ref.watch(connProvider).store.prefs,
  );
  ref.onDispose(notice.dispose);
  return notice;
});
