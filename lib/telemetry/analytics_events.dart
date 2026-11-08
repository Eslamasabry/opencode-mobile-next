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

import 'health_schema.dart';

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
  updateInstalled('update_installed'),

  /// A page came into view: `screen`, from [AnalyticsScreen].
  screenView('screen_view'),

  /// The person used a feature: `feature`, from [AnalyticsFeature].
  featureUsed('feature_used'),

  /// The app went to the background: `minutes` it was in front, a range.
  sessionLength('session_length'),

  // ---- Health and diagnostics (docs/telemetry/health-and-diagnostics.md) ----

  /// A connection attempt: `server_kind`, `result`, `fail_kind`, `ms_range`.
  connAttempt('conn_attempt'),

  /// A live connection ended: `server_kind`, `reason`, `recovered`.
  connDrop('conn_drop'),

  /// A message was sent: `result`, `fail_kind`, `first_reply_range`.
  sendResult('send_result'),

  /// A turn stopped moving: `stage`, `minutes_range`.
  turnStall('turn_stall'),

  /// Queued messages were flushed: `sent`, `failed` (count ranges).
  queueFlush('queue_flush'),

  /// A step of the built-in Linux setup finished.
  linuxSetupStep('linux_setup_step'),

  /// An agent (Paseo) was started: `agent`, `result`, `fail_kind`.
  agentStart('agent_start'),

  /// The background service ended: `reason`, `battery_exempt`.
  bgServiceEnd('bg_service_end'),

  /// An update finished: `kind`, `result`, `fail_kind`.
  updateResult('update_result'),

  /// Time to the first frame: `kind`, `ms_range`.
  appStart('app_start'),

  /// Slow frames on one kind of page, once per session: `screen`,
  /// `janky_pct_range`, `refresh_hz`.
  jank('jank'),

  /// Android asked the app to free memory: `level`.
  memoryPressure('memory_pressure');

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

/// The Node.js safe mode setup settled on (issue #117, `NodeCompatVariant`),
/// or [noneWorked] when it gave up. Anything else is sent as [other].
enum AnalyticsNodeVariant {
  armcap,
  threads,
  both,
  noneWorked,
  other;

  String get wire => this == noneWorked ? 'none_worked' : name;

  static AnalyticsNodeVariant of(String name) => values.firstWhere(
    (v) => v.wire == name || v.name == name,
    orElse: () => other,
  );
}

/// Pages, by kind. A page outside this list is sent as [other]; nothing
/// about what is on the page is ever sent.
enum AnalyticsScreen {
  conversations,
  chat,
  newChat,
  servers,
  thisPhone,
  setup,
  privacy,
  appearance,
  integrations,
  settings,
  files,
  git,
  review,
  team,
  usage,
  agents,
  about,
  other;

  String get wire => switch (this) {
    newChat => 'new_chat',
    thisPhone => 'this_phone',
    _ => name,
  };

  /// [route] is a route name (`/chat/ses_1`) or a page's type name
  /// (`ChatScreen`); only the matching word of this list is kept.
  static AnalyticsScreen of(String route) {
    final r = route.toLowerCase();
    if (r == '/' || r == '/home' || r.contains('chatshome')) {
      return conversations;
    }
    if (r.contains('newchat')) return newChat;
    if (r.startsWith('/chat/') || r.contains('chatscreen')) return chat;
    if (r.contains('thisphone') || r.contains('this-phone')) return thisPhone;
    if (r.contains('setup')) return setup;
    if (r.contains('privacy')) return privacy;
    if (r.contains('appearance')) return appearance;
    if (r.contains('integration')) return integrations;
    // "Profile" holds "file": a server's profile page is a servers page.
    if (r.contains('server') || r.contains('profile')) return servers;
    if (r.contains('setting')) return settings;
    if (r.contains('file')) return files;
    if (r.contains('git')) return git;
    if (r.contains('review') || r.contains('diff')) return review;
    if (r.contains('team')) return team;
    if (r.contains('usage')) return usage;
    if (r.contains('agent')) return agents;
    if (r.contains('about')) return about;
    return other;
  }
}

/// Features whose use is counted, once per use.
enum AnalyticsFeature {
  voice,
  camera,
  fileAttach,
  agentSwitch,
  modelSwitch,
  serverAdd,
  gitAction,
  reviewOpen;

  String get wire => switch (this) {
    fileAttach => 'file_attach',
    agentSwitch => 'agent_switch',
    modelSwitch => 'model_switch',
    serverAdd => 'server_add',
    gitAction => 'git_action',
    reviewOpen => 'review_open',
    _ => name,
  };
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
  static const sessionMinutes = [
    'under_1',
    '1_to_5',
    '5_to_15',
    '15_to_60',
    'over_60',
  ];

  static String sessionMinutesOf(Duration elapsed) {
    final m = elapsed.inSeconds / 60;
    if (m < 1) return sessionMinutes[0];
    if (m < 5) return sessionMinutes[1];
    if (m < 15) return sessionMinutes[2];
    if (m < 60) return sessionMinutes[3];
    return sessionMinutes[4];
  }

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
    'node_variant': {for (final v in AnalyticsNodeVariant.values) v.wire},
  },
  AnalyticsEvent.setupDone.wire: {
    'minutes': {...AnalyticsBuckets.minutes},
  },
  AnalyticsEvent.firstReply.wire: {
    'server_kind': {for (final k in AnalyticsServerKind.values) k.name},
    'seconds': {...AnalyticsBuckets.seconds},
  },
  AnalyticsEvent.screenView.wire: {
    'screen': {for (final s in AnalyticsScreen.values) s.wire},
  },
  AnalyticsEvent.featureUsed.wire: {
    'feature': {for (final f in AnalyticsFeature.values) f.wire},
  },
  AnalyticsEvent.sessionLength.wire: {
    'minutes': {...AnalyticsBuckets.sessionMinutes},
  },
  AnalyticsEvent.connectComputerChosen.wire: const {},
  AnalyticsEvent.updateOffered.wire: const {},
  AnalyticsEvent.updateInstalled.wire: const {},
  ..._healthSchema,
};

final Map<String, Map<String, Set<String>>> _healthSchema = {
  AnalyticsEvent.connAttempt.wire: {
    'server_kind': HealthWords.serverKinds,
    'result': HealthWords.okFail,
    'fail_kind': HealthWords.failKinds,
    'ms_range': {...HealthRanges.connMs},
  },
  AnalyticsEvent.connDrop.wire: {
    'server_kind': HealthWords.serverKinds,
    'reason': HealthWords.dropReasons,
    'recovered': HealthWords.yesNo,
  },
  AnalyticsEvent.sendResult.wire: {
    'result': {'ok', 'fail', 'queued'},
    'fail_kind': {...HealthWords.failKinds, ...HealthWords.sendOnlyFailKinds},
    'first_reply_range': {...HealthRanges.firstReply},
  },
  AnalyticsEvent.turnStall.wire: {
    'stage': HealthWords.stallStages,
    'minutes_range': {...HealthRanges.stallMinutes},
  },
  AnalyticsEvent.queueFlush.wire: {
    'sent': {...HealthRanges.counts},
    'failed': {...HealthRanges.counts},
  },
  AnalyticsEvent.linuxSetupStep.wire: {
    'step': HealthWords.linuxSteps,
    'result': HealthWords.okFail,
    'ms_range': {...HealthRanges.setupMs},
    'exit_kind': HealthWords.exitKinds,
    'signal': HealthWords.signals,
    'compat_mode': HealthWords.compatModes,
    'free_space_range': {...HealthRanges.storage},
  },
  AnalyticsEvent.agentStart.wire: {
    'agent': HealthWords.agents,
    'result': HealthWords.okFail,
    'fail_kind': HealthWords.agentFailKinds,
  },
  AnalyticsEvent.bgServiceEnd.wire: {
    'reason': HealthWords.bgReasons,
    'battery_exempt': HealthWords.yesNo,
  },
  AnalyticsEvent.updateResult.wire: {
    'kind': HealthWords.updateKinds,
    'result': HealthWords.updateResults,
    'fail_kind': HealthWords.updateFailKinds,
  },
  AnalyticsEvent.appStart.wire: {
    'kind': HealthWords.startKinds,
    'ms_range': {...HealthRanges.appStartMs},
  },
  AnalyticsEvent.jank.wire: {
    'screen': {for (final s in AnalyticsScreen.values) s.wire},
    'janky_pct_range': {...HealthRanges.jankPct},
    'refresh_hz': {...HealthRanges.refreshHz},
  },
  AnalyticsEvent.memoryPressure.wire: {'level': HealthWords.memoryLevels},
};

/// A last check on any listed word, whatever the schema says: no path
/// separators, no `@`, nothing shaped like a host or address (`a.b`), at
/// most 40 characters, and only letters, digits and `_ < > + -` (the range
/// words such as `<500`, `2-10s`, `4+`, and signal names such as `SIGILL`).
bool analyticsValueIsSafe(String value) =>
    value.isNotEmpty &&
    value.length <= 40 &&
    !value.contains('/') &&
    !value.contains('@') &&
    !RegExp(r'\w\.\w').hasMatch(value) &&
    RegExp(r'^[A-Za-z0-9_<>+\-]+$').hasMatch(value);

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
