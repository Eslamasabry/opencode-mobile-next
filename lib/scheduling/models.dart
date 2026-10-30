/// The device scheduler stores only user-authored prompts and target identifiers.
/// Credentials are resolved from the profile immediately before each dispatch.
enum ScheduleRecurrenceKind { once, daily, weekdays, interval }

enum MissedRunPolicy { catchUp, skip }

enum ScheduledPromptRunStatus { running, ran, failed, skipped }

class ScheduleRecurrence {
  const ScheduleRecurrence.once(DateTime this.at)
    : kind = ScheduleRecurrenceKind.once,
      hour = 8,
      minute = 0,
      hours = 1;

  /// Every day at a wall-clock time, kept across daylight-saving changes.
  const ScheduleRecurrence.daily({this.hour = 8, this.minute = 0})
    : kind = ScheduleRecurrenceKind.daily,
      at = null,
      hours = 1;

  const ScheduleRecurrence.weekdays({this.hour = 8, this.minute = 0})
    : kind = ScheduleRecurrenceKind.weekdays,
      at = null,
      hours = 1;

  const ScheduleRecurrence.interval({
    required this.hours,
    required DateTime anchor,
  }) : kind = ScheduleRecurrenceKind.interval,
       at = anchor,
       hour = 8,
       minute = 0;

  final ScheduleRecurrenceKind kind;

  /// Absolute once time or interval anchor; daily and weekdays use device
  /// local time.
  final DateTime? at;
  final int hour;
  final int minute;
  final int hours;

  void validate() {
    if (hour < 0 ||
        hour > 23 ||
        minute < 0 ||
        minute > 59 ||
        hours < 1 ||
        hours > 168 ||
        ((kind == ScheduleRecurrenceKind.once ||
                kind == ScheduleRecurrenceKind.interval) &&
            at == null)) {
      throw const FormatException('Invalid schedule recurrence');
    }
  }

  Map<String, Object?> toJson() => {
    'kind': kind.name,
    'at': at?.toUtc().toIso8601String(),
    'hour': hour,
    'minute': minute,
    'hours': hours,
  };

  factory ScheduleRecurrence.fromJson(Map<String, dynamic> json) {
    final kind = ScheduleRecurrenceKind.values.byName(json['kind'] as String);
    final at = json['at'] == null ? null : DateTime.parse(json['at'] as String);
    final value = switch (kind) {
      ScheduleRecurrenceKind.once => ScheduleRecurrence.once(at!),
      ScheduleRecurrenceKind.daily => ScheduleRecurrence.daily(
        hour: json['hour'] as int,
        minute: json['minute'] as int,
      ),
      ScheduleRecurrenceKind.weekdays => ScheduleRecurrence.weekdays(
        hour: json['hour'] as int,
        minute: json['minute'] as int,
      ),
      ScheduleRecurrenceKind.interval => ScheduleRecurrence.interval(
        hours: json['hours'] as int,
        anchor: at!,
      ),
    };
    value.validate();
    return value;
  }
}

class ScheduledPrompt {
  const ScheduledPrompt({
    required this.id,
    required this.profileId,
    required this.profileFingerprint,
    required this.prompt,
    required this.projectPath,
    required this.recurrence,
    required this.createdAt,
    this.workspace,
    this.sessionId,
    this.agent,
    this.modelProviderId,
    this.modelId,
    this.missedPolicy = MissedRunPolicy.catchUp,
    this.enabled = true,
    this.nextRunAt,
    this.revision = 1,
  });

  final String id;
  final String profileId;
  final String profileFingerprint;
  final String prompt;
  final String projectPath;
  final String? workspace;

  /// Null creates a new conversation for each occurrence.
  final String? sessionId;
  final String? agent;
  final String? modelProviderId;
  final String? modelId;
  final ScheduleRecurrence recurrence;
  final MissedRunPolicy missedPolicy;
  final bool enabled;
  final DateTime? nextRunAt;
  final DateTime createdAt;
  final int revision;

  void validate() {
    if (!_identifier(id) ||
        !_identifier(profileId) ||
        profileFingerprint.isEmpty ||
        profileFingerprint.length > 256 ||
        prompt.trim().isEmpty ||
        prompt.length > 32000 ||
        projectPath.trim().isEmpty ||
        projectPath.length > 4096 ||
        revision < 1 ||
        (sessionId != null && sessionId!.isEmpty) ||
        ((modelProviderId == null) != (modelId == null))) {
      throw const FormatException('Invalid scheduled prompt');
    }
    recurrence.validate();
  }

  ScheduledPrompt copyWith({
    String? prompt,
    String? projectPath,
    String? profileFingerprint,
    ScheduleRecurrence? recurrence,
    MissedRunPolicy? missedPolicy,
    bool? enabled,
    DateTime? nextRunAt,
    bool clearNextRunAt = false,
    int? revision,
  }) => ScheduledPrompt(
    id: id,
    profileId: profileId,
    profileFingerprint: profileFingerprint ?? this.profileFingerprint,
    prompt: prompt ?? this.prompt,
    projectPath: projectPath ?? this.projectPath,
    workspace: workspace,
    sessionId: sessionId,
    agent: agent,
    modelProviderId: modelProviderId,
    modelId: modelId,
    recurrence: recurrence ?? this.recurrence,
    missedPolicy: missedPolicy ?? this.missedPolicy,
    enabled: enabled ?? this.enabled,
    nextRunAt: clearNextRunAt ? null : nextRunAt ?? this.nextRunAt,
    createdAt: createdAt,
    revision: revision ?? this.revision,
  );

  Map<String, Object?> toJson() => {
    'id': id,
    'profileId': profileId,
    'profileFingerprint': profileFingerprint,
    'prompt': prompt,
    'projectPath': projectPath,
    'workspace': workspace,
    'sessionId': sessionId,
    'agent': agent,
    'modelProviderId': modelProviderId,
    'modelId': modelId,
    'recurrence': recurrence.toJson(),
    'missedPolicy': missedPolicy.name,
    'enabled': enabled,
    'nextRunAt': nextRunAt?.toUtc().toIso8601String(),
    'createdAt': createdAt.toUtc().toIso8601String(),
    'revision': revision,
  };

  factory ScheduledPrompt.fromJson(Map<String, dynamic> json) {
    final value = ScheduledPrompt(
      id: json['id'] as String,
      profileId: json['profileId'] as String,
      profileFingerprint: json['profileFingerprint'] as String,
      prompt: json['prompt'] as String,
      projectPath: json['projectPath'] as String,
      workspace: json['workspace'] as String?,
      sessionId: json['sessionId'] as String?,
      agent: json['agent'] as String?,
      modelProviderId: json['modelProviderId'] as String?,
      modelId: json['modelId'] as String?,
      recurrence: ScheduleRecurrence.fromJson(
        json['recurrence'] as Map<String, dynamic>,
      ),
      missedPolicy: MissedRunPolicy.values.byName(
        json['missedPolicy'] as String,
      ),
      enabled: json['enabled'] as bool,
      nextRunAt: json['nextRunAt'] == null
          ? null
          : DateTime.parse(json['nextRunAt'] as String),
      createdAt: DateTime.parse(json['createdAt'] as String),
      revision: json['revision'] as int,
    );
    value.validate();
    return value;
  }
}

class ScheduledPromptRun {
  const ScheduledPromptRun({
    required this.id,
    required this.scheduleId,
    required this.scheduledAt,
    required this.startedAt,
    required this.status,
    this.late = false,
    this.finishedAt,
    this.sessionId,
    this.errorCode,
  });

  /// The durable occurrence identity, never reused after an uncertain dispatch.
  final String id;
  final String scheduleId;
  final DateTime scheduledAt;
  final DateTime startedAt;

  /// `ran` means the server accepted the prompt, not that generation completed.
  final ScheduledPromptRunStatus status;
  final bool late;
  final DateTime? finishedAt;
  final String? sessionId;

  /// Fixed code only; no transport error, server payload, prompt or credential.
  final String? errorCode;

  void validate() {
    if (id.isEmpty ||
        id.length > 512 ||
        !_identifier(scheduleId) ||
        (errorCode != null &&
            !RegExp(r'^[a-z][a-z0-9_]{0,79}$').hasMatch(errorCode!))) {
      throw const FormatException('Invalid scheduled prompt run');
    }
  }

  Map<String, Object?> toJson() => {
    'id': id,
    'scheduleId': scheduleId,
    'scheduledAt': scheduledAt.toUtc().toIso8601String(),
    'startedAt': startedAt.toUtc().toIso8601String(),
    'status': status.name,
    'late': late,
    'finishedAt': finishedAt?.toUtc().toIso8601String(),
    'sessionId': sessionId,
    'errorCode': errorCode,
  };

  factory ScheduledPromptRun.fromJson(Map<String, dynamic> json) {
    final value = ScheduledPromptRun(
      id: json['id'] as String,
      scheduleId: json['scheduleId'] as String,
      scheduledAt: DateTime.parse(json['scheduledAt'] as String),
      startedAt: DateTime.parse(json['startedAt'] as String),
      status: ScheduledPromptRunStatus.values.byName(json['status'] as String),
      late: json['late'] as bool,
      finishedAt: json['finishedAt'] == null
          ? null
          : DateTime.parse(json['finishedAt'] as String),
      sessionId: json['sessionId'] as String?,
      errorCode: json['errorCode'] as String?,
    );
    value.validate();
    return value;
  }
}

class ScheduledPromptDigest {
  const ScheduledPromptDigest({
    this.enabled = false,
    this.hour = 8,
    this.minute = 0,
    this.lastDeliveredAt,
  });

  final bool enabled;
  final int hour;
  final int minute;
  final DateTime? lastDeliveredAt;

  void validate() {
    if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
      throw const FormatException('Invalid scheduled prompt digest');
    }
  }

  Map<String, Object?> toJson() => {
    'enabled': enabled,
    'hour': hour,
    'minute': minute,
    'lastDeliveredAt': lastDeliveredAt?.toUtc().toIso8601String(),
  };

  factory ScheduledPromptDigest.fromJson(Map<String, dynamic> json) {
    final value = ScheduledPromptDigest(
      enabled: json['enabled'] as bool,
      hour: json['hour'] as int,
      minute: json['minute'] as int,
      lastDeliveredAt: json['lastDeliveredAt'] == null
          ? null
          : DateTime.parse(json['lastDeliveredAt'] as String),
    );
    value.validate();
    return value;
  }
}

bool _identifier(String value) =>
    RegExp(r'^[a-zA-Z0-9_-]{1,128}$').firstMatch(value)?.end == value.length;
