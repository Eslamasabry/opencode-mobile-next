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

/// A separately identified helper exit, never an Android app-process exit.
@immutable
class AgentHelperExit {
  AgentHelperExit({
    required DateTime at,
    required this.exitCode,
    required this.possibleResourceKill,
  }) : at = at.toUtc();

  final DateTime at;
  final int? exitCode;
  final bool possibleResourceKill;

  String get description => possibleResourceKill
      ? 'Agent helper stopped (possible memory or process limit)'
      : 'Agent helper stopped unexpectedly';
}

@immutable
class AppExitHistory {
  AppExitHistory({
    required this.supported,
    Iterable<AppExitEntry> entries = const [],
    Iterable<AgentHelperExit> helperExits = const [],
    this.error,
  }) : entries = List<AppExitEntry>.unmodifiable(entries),
       helperExits = List<AgentHelperExit>.unmodifiable(helperExits);

  const AppExitHistory.unsupported()
    : supported = false,
      entries = const [],
      helperExits = const [],
      error = null;

  final bool supported;
  final List<AppExitEntry> entries;

  /// Latest unexpected exit per helper. Render with its own title and details;
  /// the Android category/importance fields above do not apply.
  final List<AgentHelperExit> helperExits;
  final DiagnosticsError? error;
}
