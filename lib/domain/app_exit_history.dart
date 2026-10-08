import 'package:flutter/foundation.dart';

import 'diagnostics_error.dart';

/// Stable diagnostic categories; they do not establish a battery-policy cause.
enum AppExitCategory {
  normal,
  update,
  forceStop,
  lowMemory,
  crash,
  killed;

  /// Structural redaction: only fixed copy, never Android's free-form text.
  String get safeDescription => switch (this) {
    normal => 'App closed',
    update => 'App updated',
    forceStop => 'App stopped',
    lowMemory => 'Phone needed memory',
    crash => 'App stopped unexpectedly',
    killed => 'Android ended the app',
  };
}

@immutable
class AppExitEntry {
  AppExitEntry({
    required this.reason,
    required this.importance,
    required DateTime at,
    required this.category,
  }) : at = at.toUtc();

  final int reason;
  final int importance;
  final DateTime at;
  final AppExitCategory category;

  /// Bounded, fixed category summary. Arbitrary descriptions cannot be stored.
  String get description => category.safeDescription;
}

@immutable
class AppExitHistory {
  AppExitHistory({
    required this.supported,
    Iterable<AppExitEntry> entries = const [],
    this.error,
  }) : entries = List<AppExitEntry>.unmodifiable(entries);

  const AppExitHistory.unsupported()
    : supported = false,
      entries = const [],
      error = null;

  final bool supported;
  final List<AppExitEntry> entries;
  final DiagnosticsError? error;
}
