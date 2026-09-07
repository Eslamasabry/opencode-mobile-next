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

enum MonitoredRequestKind { permission, question, form }

class ProfileNotifyRules {
  const ProfileNotifyRules({
    this.enabled = false,
    this.notifications = true,
    this.wifiOnly = false,
    this.quietStart,
    this.quietEnd,
  });
  final bool enabled;
  final bool notifications;
  final bool wifiOnly;

  /// Local wall-clock minutes after midnight; null disables quiet hours.
  final int? quietStart;
  final int? quietEnd;
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
  }) => ProfileNotifyRules(
    enabled: enabled ?? this.enabled,
    notifications: notifications ?? this.notifications,
    wifiOnly: wifiOnly ?? this.wifiOnly,
    quietStart: clearQuiet ? null : quietStart ?? this.quietStart,
    quietEnd: clearQuiet ? null : quietEnd ?? this.quietEnd,
  );
  Map<String, Object?> toJson() => {
    'enabled': enabled,
    'notifications': notifications,
    'wifiOnly': wifiOnly,
    'quietStart': quietStart,
    'quietEnd': quietEnd,
  };
  factory ProfileNotifyRules.fromJson(Map<String, dynamic> value) {
    int? minute(Object? v) => v is int && v >= 0 && v < 1440 ? v : null;
    return ProfileNotifyRules(
      enabled: value['enabled'] == true,
      notifications: value['notifications'] != false,
      wifiOnly: value['wifiOnly'] == true,
      quietStart: minute(value['quietStart']),
      quietEnd: minute(value['quietEnd']),
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
  final int? runningCount;
  final bool complete;
  bool get isCurrent => status == ProfileMonitorStatus.current && complete;
  int? get pendingCount => isCurrent ? requests.length : null;
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
