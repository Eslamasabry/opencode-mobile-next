import 'package:flutter/foundation.dart';

import 'diagnostics_error.dart';

enum BackgroundPauseReason {
  none,
  timeLimit,
  batteryRestricted,
  userStopped,
  interrupted,
}

/// A native service receipt, independent of any selected remote server.
@immutable
class BackgroundPauseState {
  const BackgroundPauseState({
    required this.supported,
    this.active = false,
    this.paused = false,
    this.reason = BackgroundPauseReason.none,
    this.at,
    this.canResume = false,
  });

  static const unsupported = BackgroundPauseState(supported: false);

  final bool supported;
  final bool active;
  final bool paused;
  final BackgroundPauseReason reason;
  final DateTime? at;
  final bool canResume;

  /// Strict structural parsing never converts arbitrary channel values to text.
  static BackgroundPauseState fromPlatform(Object? value) {
    if (value is! Map || value['supported'] != true) return unsupported;
    if (value['active'] is! bool ||
        value['paused'] is! bool ||
        value['canResume'] is! bool) {
      return unsupported;
    }
    final active = value['active'] == true;
    final reason = BackgroundPauseReason.values.firstWhere(
      (reason) => reason.name == value['reason'],
      orElse: () => BackgroundPauseReason.none,
    );
    final timestamp = value['at'];
    final at = timestamp is int && timestamp > 0 && timestamp < 253402300800000
        ? DateTime.fromMillisecondsSinceEpoch(timestamp, isUtc: true)
        : null;
    final paused = !active && value['paused'] == true;
    if (paused && (reason == BackgroundPauseReason.none || at == null)) {
      return unsupported;
    }
    return BackgroundPauseState(
      supported: true,
      active: active,
      paused: paused,
      reason: paused ? reason : BackgroundPauseReason.none,
      at: paused ? at : null,
      canResume: paused && value['canResume'] == true,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is BackgroundPauseState &&
      other.supported == supported &&
      other.active == active &&
      other.paused == paused &&
      other.reason == reason &&
      other.at == at &&
      other.canResume == canResume;

  @override
  int get hashCode =>
      Object.hash(supported, active, paused, reason, at, canResume);
}

@immutable
class BackgroundResumeResult {
  const BackgroundResumeResult(this.state, {this.error});

  final BackgroundPauseState state;
  final DiagnosticsError? error;
}
