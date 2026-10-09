part of '../models.dart';

// ---------------- Events / permissions ----------------

class EventEnvelope {
  final String type;
  final Map<String, dynamic> properties;
  final String? directory;
  final String? project;
  final String? workspace;

  EventEnvelope({
    required this.type,
    this.properties = const {},
    this.directory,
    this.project,
    this.workspace,
  });

  factory EventEnvelope.fromJson(Map<String, dynamic> j) => EventEnvelope(
    type: (j['type'] ?? '').toString(),
    properties: j['properties'] is Map<String, dynamic>
        ? j['properties'] as Map<String, dynamic>
        : const {},
  );

  factory EventEnvelope.fromGlobalJson(Map<String, dynamic> j) {
    final payload = j['payload'];
    final event = payload is Map
        ? EventEnvelope.fromJson(Map<String, dynamic>.from(payload))
        : EventEnvelope(type: '');
    return EventEnvelope(
      type: event.type,
      properties: event.properties,
      directory: j['directory']?.toString(),
      project: j['project']?.toString(),
      workspace: j['workspace']?.toString(),
    );
  }
}

class PermissionTool {
  final String messageID;
  final String callID;

  PermissionTool({required this.messageID, required this.callID});

  factory PermissionTool.fromJson(Map<String, dynamic> json) => PermissionTool(
    messageID: (json['messageID'] ?? '').toString(),
    callID: (json['callID'] ?? '').toString(),
  );
}

class PermissionRequest {
  final String id;
  final String sessionID;
  final String permission;
  final List<String> patterns;
  final Map<String, dynamic> metadata;
  final List<String> always;
  final PermissionTool? tool;

  /// False when the server can take no standing rule for this request (Paseo
  /// keeps one only when the agent suggested it): the card then offers no
  /// "Always allow".
  final bool canAlwaysAllow;

  /// Optional human context supplied with an OpenCode 2 request; v1
  /// requests never carry one.
  final String? message;

  PermissionRequest({
    required this.id,
    required this.sessionID,
    required this.permission,
    this.patterns = const [],
    this.metadata = const {},
    this.always = const [],
    this.tool,
    this.message,
    this.canAlwaysAllow = true,
  });

  factory PermissionRequest.fromJson(Map<String, dynamic> json) =>
      PermissionRequest(
        id: (json['id'] ?? '').toString(),
        sessionID: (json['sessionID'] ?? '').toString(),
        permission: (json['permission'] ?? '').toString(),
        patterns: json['patterns'] is List
            ? (json['patterns'] as List).map((item) => item.toString()).toList()
            : const [],
        metadata: json['metadata'] is Map
            ? Map<String, dynamic>.from(json['metadata'] as Map)
            : const {},
        always: json['always'] is List
            ? (json['always'] as List).map((item) => item.toString()).toList()
            : const [],
        tool: json['tool'] is Map
            ? PermissionTool.fromJson(
                Map<String, dynamic>.from(json['tool'] as Map),
              )
            : null,
        message: json['message']?.toString(),
      );

  static const _commandPermissions = {'bash', 'shell'};
  static const _fileMetadataKeys = [
    'filePath',
    'filepath',
    'file_path',
    'path',
    'file',
  ];
  static const _filePermissions = {
    'edit',
    'write',
    'read',
    'multiedit',
    'patch',
    'external_directory',
  };

  /// Shell command awaiting approval, taken from `metadata.command`
  /// (the v1 bash tool's shape) when present; falls back to the first
  /// pattern for `bash`/`shell` asks, since those patterns are the command.
  String? get commandPreview {
    final command = _firstString(['command', 'cmd']);
    if (command != null) return command;
    if (_commandPermissions.contains(permission.toLowerCase())) {
      return _firstPattern();
    }
    return null;
  }

  /// File the ask concerns, from `metadata.filePath` (or the common
  /// spellings) when present, else the first pattern of a file-scoped
  /// permission (`edit`, `write`, `read`, `external_directory`, ...).
  String? get filePath {
    final path = _firstString(_fileMetadataKeys);
    if (path != null) return path;
    if (_filePermissions.contains(permission.toLowerCase())) {
      return _firstPattern();
    }
    return null;
  }

  String? _firstString(List<String> keys) {
    for (final key in keys) {
      final value = metadata[key];
      if (value is String && value.trim().isNotEmpty) return value;
    }
    return null;
  }

  String? _firstPattern() {
    for (final pattern in patterns) {
      if (pattern.trim().isNotEmpty && pattern != '*') return pattern;
    }
    return null;
  }
}

// ---------------- Session retry state ----------------

/// A session's provider-retry backoff, from `session.status`
/// `{type: 'retry', attempt, message, next}` (v1 and v2) or the v2
/// `session.retry.scheduled` event. Cleared once the session goes busy/idle.
/// What the server asks the person to do about a retry it will not get past
/// by itself (`action` on a retry status): a title and message in the
/// server's words, and a labelled link (an upgrade page, a billing screen).
class SessionRetryAction {
  final String title;
  final String message;
  final String label;

  /// An address the server supplied: opened only through `openExternalLink`.
  final String? link;

  const SessionRetryAction({
    required this.title,
    required this.message,
    required this.label,
    this.link,
  });

  static SessionRetryAction? fromJson(dynamic v) {
    if (v is! Map) return null;
    final title = v['title']?.toString().trim() ?? '';
    final message = v['message']?.toString().trim() ?? '';
    if (title.isEmpty && message.isEmpty) return null;
    final link = v['link']?.toString().trim();
    return SessionRetryAction(
      title: title,
      message: message,
      label: v['label']?.toString().trim() ?? '',
      link: link == null || link.isEmpty ? null : link,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is SessionRetryAction &&
      other.title == title &&
      other.message == message &&
      other.label == label &&
      other.link == link;

  @override
  int get hashCode => Object.hash(title, message, label, link);
}

class SessionRetryState {
  final int attempt;
  final String? message;

  /// What to do about it, when the server says (v1 `action`).
  final SessionRetryAction? action;

  /// When the next attempt is scheduled; null when the server gave no time.
  final DateTime? next;

  const SessionRetryState({
    required this.attempt,
    this.message,
    this.next,
    this.action,
  });

  /// Parses a status object; null unless `type` is `retry`.
  static SessionRetryState? fromStatusJson(dynamic v) {
    if (v is! Map || v['type']?.toString() != 'retry') return null;
    final next = _asInt(v['next']);
    return SessionRetryState(
      attempt: _asInt(v['attempt']) ?? 0,
      message: v['message']?.toString(),
      action: SessionRetryAction.fromJson(v['action']),
      next: next == null || next <= 0
          ? null
          : DateTime.fromMillisecondsSinceEpoch(next),
    );
  }

  /// Time remaining until [next]; null when unknown, never negative.
  Duration? get remaining {
    final at = next;
    if (at == null) return null;
    final delta = at.difference(DateTime.now());
    return delta.isNegative ? Duration.zero : delta;
  }

  @override
  bool operator ==(Object other) =>
      other is SessionRetryState &&
      other.attempt == attempt &&
      other.message == message &&
      other.action == action &&
      other.next == next;

  @override
  int get hashCode => Object.hash(attempt, message, next, action);

  @override
  String toString() =>
      'SessionRetryState(attempt: $attempt, message: $message, next: $next)';
}
