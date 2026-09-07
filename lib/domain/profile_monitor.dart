/// Metadata for the last selected location of one explicitly monitored profile.
/// These objects never contain transport credentials or raw server errors.
enum ProfileMonitorStatus {
  disabled,
  waiting,
  checking,
  current,
  unavailable,
  wifiRequired,
  paused,
}

/// [checkIn] is not a server request: it is this app's own reminder that a
/// session has been observed busy for longer than the profile's rule.
enum MonitoredRequestKind { permission, question, form, checkIn }

class ProfileNotifyRules {
  const ProfileNotifyRules({
    this.enabled = false,
    this.notifications = true,
    this.wifiOnly = false,
    this.quietStart,
    this.quietEnd,
    this.checkInAfterMinutes,
  });

  /// The durations the settings screen offers for [checkInAfterMinutes].
  static const checkInChoices = [15, 30, 60, 120];
  static const defaultCheckInMinutes = 30;

  final bool enabled;
  final bool notifications;
  final bool wifiOnly;

  /// Local wall-clock minutes after midnight; null disables quiet hours.
  final int? quietStart;
  final int? quietEnd;

  /// Remind once per observed busy interval after this many minutes of
  /// observed work; null (the default) never reminds.
  final int? checkInAfterMinutes;
  Duration? get checkInAfter => checkInAfterMinutes == null
      ? null
      : Duration(minutes: checkInAfterMinutes!);
  bool quietAt(DateTime now) {
    final start = quietStart;
    final end = quietEnd;
    if (start == null || end == null) return false;
    final minute = now.hour * 60 + now.minute;
    return start == end ||
        (start < end
            ? minute >= start && minute < end
            : minute >= start || minute < end);
  }

  ProfileNotifyRules copyWith({
    bool? enabled,
    bool? notifications,
    bool? wifiOnly,
    int? quietStart,
    int? quietEnd,
    bool clearQuiet = false,
    int? checkInAfterMinutes,
    bool clearCheckIn = false,
  }) => ProfileNotifyRules(
    enabled: enabled ?? this.enabled,
    notifications: notifications ?? this.notifications,
    wifiOnly: wifiOnly ?? this.wifiOnly,
    quietStart: clearQuiet ? null : quietStart ?? this.quietStart,
    quietEnd: clearQuiet ? null : quietEnd ?? this.quietEnd,
    checkInAfterMinutes: clearCheckIn
        ? null
        : checkInAfterMinutes ?? this.checkInAfterMinutes,
  );
  Map<String, Object?> toJson() => {
    'enabled': enabled,
    'notifications': notifications,
    'wifiOnly': wifiOnly,
    'quietStart': quietStart,
    'quietEnd': quietEnd,
    'checkInAfterMinutes': checkInAfterMinutes,
  };
  factory ProfileNotifyRules.fromJson(Map<String, dynamic> value) {
    int? minute(Object? v) => v is int && v >= 0 && v < 1440 ? v : null;
    final checkIn = value['checkInAfterMinutes'];
    return ProfileNotifyRules(
      enabled: value['enabled'] == true,
      notifications: value['notifications'] != false,
      wifiOnly: value['wifiOnly'] == true,
      quietStart: minute(value['quietStart']),
      quietEnd: minute(value['quietEnd']),
      // Only a positive whole number of minutes is a rule; anything else
      // (an older build's payload, a corrupt value) reads as off.
      checkInAfterMinutes: checkIn is int && checkIn > 0 && checkIn <= 24 * 60
          ? checkIn
          : null,
    );
  }
}

/// One stretch of polls during which a session was seen busy.
///
/// This is an observation, not the server's record of a run: the monitor
/// only samples status on its poll interval, so the true start lies
/// somewhere before [firstObservedBusyAt], and a session that went idle and
/// busy again between two polls looks like one interval. Duration claims
/// derived from it are "observed for at least", never exact.
class ObservedBusyInterval {
  const ObservedBusyInterval({
    required this.sessionID,
    required this.firstObservedBusyAt,
    required this.lastObservedBusyAt,
    this.title,
    this.directory,
    this.workspace,
  });
  final String sessionID;
  final DateTime firstObservedBusyAt;
  final DateTime lastObservedBusyAt;
  final String? title;
  final String? directory;
  final String? workspace;

  /// Stable within one interval; a later interval for the same session gets
  /// a different id, so a reminder key derived from it fires again for it.
  String get id => 'busy-${firstObservedBusyAt.millisecondsSinceEpoch}';

  /// How long the session has been seen busy, measured from the first
  /// observation to the latest one — not to [now], because the monitor
  /// cannot vouch for anything between polls.
  Duration get observedFor =>
      lastObservedBusyAt.difference(firstObservedBusyAt);

  /// Whether [rules] asks for a reminder and this interval has been observed
  /// for at least that long.
  bool isDue(ProfileNotifyRules rules) {
    final after = rules.checkInAfter;
    return after != null && observedFor >= after;
  }

  ObservedBusyInterval observedAgainAt(
    DateTime at, {
    String? title,
    String? directory,
    String? workspace,
  }) => ObservedBusyInterval(
    sessionID: sessionID,
    firstObservedBusyAt: firstObservedBusyAt,
    lastObservedBusyAt: at,
    title: title ?? this.title,
    directory: directory ?? this.directory,
    workspace: workspace ?? this.workspace,
  );

  MonitoredRequest toRequest() => MonitoredRequest(
    id: id,
    sessionID: sessionID,
    kind: MonitoredRequestKind.checkIn,
    title: title,
    directory: directory,
    workspace: workspace,
  );

  Map<String, Object?> toJson() => {
    'sessionID': sessionID,
    'firstObservedBusyAt': firstObservedBusyAt.millisecondsSinceEpoch,
    'lastObservedBusyAt': lastObservedBusyAt.millisecondsSinceEpoch,
    'title': title,
    'directory': directory,
    'workspace': workspace,
  };

  static ObservedBusyInterval? fromJson(Object? value) {
    if (value is! Map) return null;
    final sessionID = value['sessionID']?.toString() ?? '';
    final first = value['firstObservedBusyAt'];
    final last = value['lastObservedBusyAt'];
    if (sessionID.isEmpty || first is! int || last is! int || last < first) {
      return null;
    }
    return ObservedBusyInterval(
      sessionID: sessionID,
      firstObservedBusyAt: DateTime.fromMillisecondsSinceEpoch(first),
      lastObservedBusyAt: DateTime.fromMillisecondsSinceEpoch(last),
      title: value['title']?.toString(),
      directory: value['directory']?.toString(),
      workspace: value['workspace']?.toString(),
    );
  }
}

class MonitoredRequest {
  const MonitoredRequest({
    required this.id,
    required this.sessionID,
    required this.kind,
    this.title,
    this.directory,
    this.workspace,
  });
  final String id;
  final String sessionID;
  final MonitoredRequestKind kind;
  final String? title;
  final String? directory;
  final String? workspace;
  String get identity => '${kind.name}:$sessionID:$id';
}

class ProfileAttentionSnapshot {
  const ProfileAttentionSnapshot({
    required this.profileID,
    required this.status,
    this.checkedAt,
    this.directory,
    this.workspace,
    this.requests = const [],
    this.busyIntervals = const [],
    this.runningCount,
    this.complete = false,
    this.nextCheckAt,
  });
  final String profileID;
  final ProfileMonitorStatus status;
  final DateTime? checkedAt;
  final DateTime? nextCheckAt;
  final String? directory;
  final String? workspace;
  final List<MonitoredRequest> requests;

  /// Sessions seen busy on the latest successful poll, oldest first. Kept
  /// apart from [requests]: a long run is something to check in on, not a
  /// pending request, so it never counts as attention.
  final List<ObservedBusyInterval> busyIntervals;
  final int? runningCount;
  final bool complete;
  bool get isCurrent => status == ProfileMonitorStatus.current && complete;
  int? get pendingCount => isCurrent ? requests.length : null;

  /// The intervals [rules] would remind about right now.
  List<ObservedBusyInterval> dueCheckIns(ProfileNotifyRules rules) => [
    for (final interval in busyIntervals)
      if (interval.isDue(rules)) interval,
  ];
}

class MonitoredRoute {
  const MonitoredRoute({
    required this.profileID,
    required this.requestID,
    required this.sessionID,
    required this.kind,
    required this.createdAt,
    required this.serverUrl,
    required this.sourceIdentity,
    this.directory,
    this.workspace,
  });
  final String profileID, requestID, sessionID, serverUrl, sourceIdentity;
  final MonitoredRequestKind kind;
  final DateTime createdAt;
  final String? directory, workspace;
  Map<String, Object?> toJson() => {
    'requestID': requestID,
    'sessionID': sessionID,
    'kind': kind.name,
    'createdAt': createdAt.millisecondsSinceEpoch,
    'serverUrl': serverUrl,
    'sourceIdentity': sourceIdentity,
    'directory': directory,
    'workspace': workspace,
  };
  factory MonitoredRoute.fromJson(
    String profileID,
    Map<String, dynamic> value,
  ) => MonitoredRoute(
    profileID: profileID,
    requestID: value['requestID'] as String,
    sessionID: value['sessionID'] as String,
    kind: MonitoredRequestKind.values.byName(value['kind'] as String),
    createdAt: DateTime.fromMillisecondsSinceEpoch(value['createdAt'] as int),
    serverUrl: value['serverUrl'] as String,
    sourceIdentity: value['sourceIdentity'] as String? ?? '',
    directory: value['directory'] as String?,
    workspace: value['workspace'] as String?,
  );
}
