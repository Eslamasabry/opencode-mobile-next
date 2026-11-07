/// Product analytics: the complete list of what may ever be counted.
///
/// Every event name and every property value comes from this file. There is
/// no free text: a value is one of a fixed set of short words, a bucket, or
/// true/false. [TelemetryService.track] drops any event or property that is
/// not listed here, so a call site cannot widen what leaves the phone.
///
/// Same switch as crash reports (Settings > Privacy and data), off by default.
/// PRIVACY.md and the README list exactly these events; change them together.
library;

/// The events. [wire] is the name the vendor receives.
enum AnalyticsEvent {
  /// The app came to the front: `kind` cold (a new process) or warm.
  appOpen('app_open'),

  /// A setup step started: `step`.
  setupStepReached('setup_step_reached'),

  /// A setup step failed: `step`, `kind`, `compat_mode`, `node_variant`.
  setupStepFailed('setup_step_failed'),

  /// A setup job finished: `minutes` (a bucket).
  setupDone('setup_done'),

  /// The first reply this install ever saw: `server_kind`, `seconds` (a
  /// bucket).
  firstReply('first_reply'),

  /// The person chose to connect a computer.
  connectComputerChosen('connect_computer_chosen'),

  /// An app update was offered (ready to install on restart).
  updateOffered('update_offered'),

  /// The app started on a newer version than last time.
  updateInstalled('update_installed');

  const AnalyticsEvent(this.wire);
  final String wire;
}

/// Setup steps, as the setup engine names them (`SetupComponentIds`).
/// Anything else is counted as [other].
enum AnalyticsSetupStep {
  linux,
  essentials,
  python,
  node,
  opencode,
  aiteam,
  voice,
  start,
  other;

  static AnalyticsSetupStep of(String id) =>
      values.firstWhere((step) => step.name == id, orElse: () => other);
}

/// Why a setup step failed, in five words.
enum AnalyticsFailureKind { crash, timeout, network, space, other }

/// Where the server that answered runs.
enum AnalyticsServerKind { phone, computer }

/// Node.js builds the setup can pick between (issue #117). Only these names
/// are ever sent; an unknown build is sent as [other].
enum AnalyticsNodeVariant {
  standard,
  compat,
  legacy,
  other;

  static AnalyticsNodeVariant of(String name) =>
      values.firstWhere((v) => v.name == name, orElse: () => other);
}

/// Duration buckets: the vendor never sees an exact time.
abstract final class AnalyticsBuckets {
  static const minutes = [
    'under_2',
    '2_to_5',
    '5_to_10',
    '10_to_20',
    '20_to_40',
    'over_40',
  ];
  static const seconds = [
    'under_5',
    '5_to_15',
    '15_to_30',
    '30_to_60',
    '60_to_180',
    'over_180',
    'unknown',
  ];

  static String minutesOf(Duration elapsed) {
    final m = elapsed.inSeconds / 60;
    if (m < 2) return minutes[0];
    if (m < 5) return minutes[1];
    if (m < 10) return minutes[2];
    if (m < 20) return minutes[3];
    if (m < 40) return minutes[4];
    return minutes[5];
  }

  static String secondsOf(Duration? elapsed) {
    if (elapsed == null || elapsed.isNegative) return 'unknown';
    final s = elapsed.inMilliseconds / 1000;
    if (s < 5) return seconds[0];
    if (s < 15) return seconds[1];
    if (s < 30) return seconds[2];
    if (s < 60) return seconds[3];
    if (s < 180) return seconds[4];
    return seconds[5];
  }
}

const _bool = {'true', 'false'};

/// For each event, its properties and every value each may take.
final Map<String, Map<String, Set<String>>> analyticsSchema = {
  AnalyticsEvent.appOpen.wire: {
    'kind': {'cold', 'warm'},
  },
  AnalyticsEvent.setupStepReached.wire: {
    'step': {for (final s in AnalyticsSetupStep.values) s.name},
  },
  AnalyticsEvent.setupStepFailed.wire: {
    'step': {for (final s in AnalyticsSetupStep.values) s.name},
    'kind': {for (final k in AnalyticsFailureKind.values) k.name},
    'compat_mode': _bool,
    'node_variant': {for (final v in AnalyticsNodeVariant.values) v.name},
  },
  AnalyticsEvent.setupDone.wire: {
    'minutes': {...AnalyticsBuckets.minutes},
  },
  AnalyticsEvent.firstReply.wire: {
    'server_kind': {for (final k in AnalyticsServerKind.values) k.name},
    'seconds': {...AnalyticsBuckets.seconds},
  },
  AnalyticsEvent.connectComputerChosen.wire: const {},
  AnalyticsEvent.updateOffered.wire: const {},
  AnalyticsEvent.updateInstalled.wire: const {},
};

/// A last check on any value, whatever the schema says: no path separators,
/// no `@`, nothing shaped like a host or address (`a.b`), at most 40
/// characters, and plain lower-case words, digits and `_` only.
bool analyticsValueIsSafe(String value) =>
    value.isNotEmpty &&
    value.length <= 40 &&
    !value.contains('/') &&
    !value.contains('@') &&
    !RegExp(r'\w\.\w').hasMatch(value) &&
    RegExp(r'^[a-z0-9_]+$').hasMatch(value);

/// Classifies a failed step from its exit code and error text into one of
/// [AnalyticsFailureKind]. Only the resulting word leaves the phone.
AnalyticsFailureKind analyticsFailureKind({int? exit, String? error}) {
  final text = (error ?? '').toLowerCase();
  if (exit == 139 ||
      exit == 134 ||
      exit == 132 ||
      text.contains('segmentation fault') ||
      text.contains('illegal instruction') ||
      text.contains('aborted') ||
      text.contains('core dumped')) {
    return AnalyticsFailureKind.crash;
  }
  if (exit == 124 || text.contains('timed out') || text.contains('timeout')) {
    return AnalyticsFailureKind.timeout;
  }
  if (text.contains('no space') ||
      text.contains('enospc') ||
      text.contains('storage') ||
      text.contains('disk full')) {
    return AnalyticsFailureKind.space;
  }
  if (text.contains('network') ||
      text.contains('could not resolve') ||
      text.contains('connection') ||
      text.contains('unreachable') ||
      text.contains('download') ||
      text.contains('http') ||
      text.contains('tls') ||
      text.contains('ssl')) {
    return AnalyticsFailureKind.network;
  }
  return AnalyticsFailureKind.other;
}
