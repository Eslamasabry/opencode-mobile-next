import 'dart:convert';
import 'dart:typed_data';

part 'models/models_messages.dart';
part 'models/models_requests.dart';
part 'models/models_events.dart';

/// Data types for the opencode HTTP server API.
/// Hand-written parsers keep full control over the loose shapes the server sends.

class Health {
  final bool healthy;
  final String? version;
  Health({required this.healthy, this.version});

  factory Health.fromJson(Map<String, dynamic> j) => Health(
    healthy: (j['healthy'] as bool?) ?? false,
    version: j['version'] as String?,
  );
}

// ---------------- Sessions ----------------

class SessionTime {
  final int? created;
  final int? updated;
  final int? archived;

  /// v2 completion and acknowledged-completion watermarks (server epoch ms).
  final int? idle;
  final int? viewed;

  /// Epoch millis at which the server started compacting the session's
  /// context (v1 `time.compacting`); null when no compaction is running.
  final int? compacting;
  SessionTime({
    this.created,
    this.updated,
    this.archived,
    this.compacting,
    this.idle,
    this.viewed,
  });

  SessionTime withReadState({int? idle, int? viewed}) => SessionTime(
    created: created,
    updated: updated,
    archived: archived,
    compacting: compacting,
    idle: idle ?? this.idle,
    viewed: viewed ?? this.viewed,
  );

  factory SessionTime.fromJson(dynamic v) {
    if (v is Map<String, dynamic>) {
      return SessionTime(
        created: _asInt(v['created']),
        updated: _asInt(v['updated']),
        archived: _asInt(v['archived']),
        compacting: _asInt(v['compacting']),
        idle: _asInt(v['idle']),
        viewed: _asInt(v['viewed']),
      );
    }
    return SessionTime();
  }
}

/// Session-level token usage. Same shape as the per-message [Tokens]
/// (input/output/reasoning plus cache read/write), aggregated by the server
/// across the whole session.
typedef SessionTokens = Tokens;

/// Aggregate diff summary a v1 server attaches to a session
/// (`summary{additions,deletions,files}`).
class SessionDiffSummary {
  final int additions;
  final int deletions;
  final int files;
  const SessionDiffSummary({
    this.additions = 0,
    this.deletions = 0,
    this.files = 0,
  });

  static SessionDiffSummary? fromJson(dynamic v) {
    if (v is! Map) return null;
    return SessionDiffSummary(
      additions: _asInt(v['additions']) ?? 0,
      deletions: _asInt(v['deletions']) ?? 0,
      files: _asInt(v['files']) ?? 0,
    );
  }
}

/// Server-owned selection. Known null values mean server inheritance;
/// unknown fields have not been hydrated yet.
class SessionSelection {
  final ModelRef? model;
  final String variant;
  final String? agent;
  final bool modelKnown;
  final bool agentKnown;
  const SessionSelection({
    this.model,
    this.variant = '',
    this.agent,
    this.modelKnown = true,
    this.agentKnown = true,
  });

  factory SessionSelection.fromJson(Map<String, dynamic> json) {
    final raw = json['model'];
    return SessionSelection(
      model:
          raw is Map &&
              raw['providerID'] is String &&
              (raw['id'] ?? raw['modelID']) is String
          ? ModelRef(
              providerID: raw['providerID'],
              modelID: raw['id'] ?? raw['modelID'],
            ).normalized
          : null,
      variant: raw is Map ? raw['variant']?.toString() ?? '' : '',
      agent: json['agent']?.toString(),
      modelKnown: json.containsKey('model'),
      agentKnown: json.containsKey('agent'),
    );
  }

  SessionSelection withModel(ModelRef? model, String variant) =>
      SessionSelection(
        model: model,
        variant: variant,
        agent: agent,
        agentKnown: agentKnown,
      );
  SessionSelection withAgent(String? agent) => SessionSelection(
    model: model,
    variant: variant,
    agent: agent,
    modelKnown: modelKnown,
  );
}

const _unchangedSessionField = Object();

/// A server-owned staged boundary and its optional, fixed file preview.
class SessionRevert {
  final String messageID;
  final String? partID;
  final String? snapshot;
  final List<FileDiff>? files;

  SessionRevert({
    required this.messageID,
    this.partID,
    this.snapshot,
    List<FileDiff>? files,
  }) : files = files == null ? null : List.unmodifiable(files);

  static SessionRevert? fromJson(dynamic value) {
    if (value is! Map ||
        value['messageID'] is! String ||
        (value['messageID'] as String).isEmpty) {
      return null;
    }
    return SessionRevert(
      messageID: value['messageID'],
      partID: value['partID'] as String?,
      snapshot: value['snapshot'] as String?,
      files: value['files'] is List
          ? [
              for (final file in value['files'] as List)
                if (file is Map)
                  FileDiff.fromJson(Map<String, dynamic>.from(file)),
            ]
          : null,
    );
  }

  /// Includes the reviewed content, not just the message ID: another client
  /// can stage the same boundary with different file behavior.
  String get fingerprint => jsonEncode([
    messageID,
    partID,
    snapshot,
    files
        ?.map(
          (f) => [
            f.file,
            f.before,
            f.after,
            f.patch,
            f.additions,
            f.deletions,
            f.status,
          ],
        )
        .toList(),
  ]);
}

class Session {
  final String id;
  final String? title;
  final String? projectID;
  final String? workspaceID;
  final String? parentID;
  final String? directory;
  final String? path;
  final bool reverted;
  final SessionRevert? stagedRevert;
  final String? shareUrl;
  final SessionTime? time;

  /// Accumulated USD spend for the session; null when the server sent none.
  final double? cost;

  /// Accumulated token usage for the session; null when the server sent none.
  final SessionTokens? tokens;

  /// Aggregate file diff counts (v1 only; v2 sessions never carry one).
  final SessionDiffSummary? summary;

  /// Agent the session currently runs with, as the server names it.
  final String? agent;

  /// Selected model as `providerID/modelID` (the server's `model` ref
  /// flattened); null when the session has no explicit model.
  final String? model;
  final SessionSelection? selection;

  /// When the server started compacting this session's context; null when
  /// no compaction is in progress.
  final DateTime? compactingSince;

  /// OpenCode 2 only: the server says the last run ended in failure.
  final bool lastRunFailed;

  Session({
    required this.id,
    this.title,
    this.projectID,
    this.workspaceID,
    this.parentID,
    this.directory,
    this.path,
    this.reverted = false,
    this.stagedRevert,
    this.shareUrl,
    this.time,
    this.cost,
    this.tokens,
    this.summary,
    this.agent,
    this.model,
    this.selection,
    this.compactingSince,
    this.lastRunFailed = false,
  });

  factory Session.fromJson(Map<String, dynamic> j) {
    final time = SessionTime.fromJson(j['time']);
    return Session(
      id: j['id'] as String,
      title: j['title'] as String?,
      projectID: j['projectID'] as String?,
      workspaceID: j['workspaceID'] as String?,
      parentID: j['parentID'] as String?,
      directory: j['directory'] as String?,
      path: j['path'] as String?,
      reverted: j['revert'] != null,
      stagedRevert: SessionRevert.fromJson(j['revert']),
      shareUrl: j['share'] is Map
          ? (j['share'] as Map)['url']?.toString()
          : null,
      time: time,
      cost: j['cost'] is num ? (j['cost'] as num).toDouble() : null,
      tokens: j['tokens'] is Map ? Tokens.fromJson(j['tokens']) : null,
      summary: SessionDiffSummary.fromJson(j['summary']),
      agent: j['agent']?.toString(),
      model: modelRefString(j['model']),
      selection: j['serverSelection'] == true
          ? SessionSelection.fromJson(j)
          : null,
      compactingSince: time.compacting == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(time.compacting!),
    );
  }

  bool get archived => time?.archived != null;

  /// Copy with updated live usage (used by `session.usage.updated`) or any
  /// other field; omitted arguments keep their current value.
  Session copyWith({
    String? title,
    double? cost,
    SessionTokens? tokens,
    SessionDiffSummary? summary,
    Object? agent = _unchangedSessionField,
    Object? model = _unchangedSessionField,
    Object? directory = _unchangedSessionField,
    Object? workspaceID = _unchangedSessionField,
    Object? projectID = _unchangedSessionField,
    Object? path = _unchangedSessionField,
    Object? stagedRevert = _unchangedSessionField,
    SessionSelection? selection,
    SessionTime? time,
  }) => Session(
    id: id,
    title: title ?? this.title,
    projectID: identical(projectID, _unchangedSessionField)
        ? this.projectID
        : projectID as String?,
    workspaceID: identical(workspaceID, _unchangedSessionField)
        ? this.workspaceID
        : workspaceID as String?,
    parentID: parentID,
    directory: identical(directory, _unchangedSessionField)
        ? this.directory
        : directory as String?,
    path: identical(path, _unchangedSessionField) ? this.path : path as String?,
    reverted: identical(stagedRevert, _unchangedSessionField)
        ? reverted
        : stagedRevert != null,
    stagedRevert: identical(stagedRevert, _unchangedSessionField)
        ? this.stagedRevert
        : stagedRevert as SessionRevert?,
    shareUrl: shareUrl,
    time: time ?? this.time,
    cost: cost ?? this.cost,
    tokens: tokens ?? this.tokens,
    summary: summary ?? this.summary,
    agent: identical(agent, _unchangedSessionField)
        ? this.agent
        : agent as String?,
    model: identical(model, _unchangedSessionField)
        ? this.model
        : model as String?,
    selection: selection ?? this.selection,
    compactingSince: compactingSince,
    lastRunFailed: lastRunFailed,
  );
}

/// Flattens a server model reference to `providerID/modelID`. Accepts the
/// `{providerID, id}` (v2 / v1 session) and `{providerID, modelID}` (v1
/// agent) object shapes as well as an already-flat string.
String? modelRefString(dynamic v) {
  if (v is String) return v.isEmpty ? null : v;
  if (v is! Map) return null;
  final id = (v['id'] ?? v['modelID'])?.toString();
  if (id == null || id.isEmpty) return null;
  final provider = v['providerID']?.toString();
  return provider == null || provider.isEmpty ? id : '$provider/$id';
}

// ---------------- Helpers ----------------

int? _asInt(dynamic v) {
  if (v is int) return v;
  if (v is num) return v.toInt();
  if (v is String) return int.tryParse(v);
  return null;
}

double _asDouble(dynamic v) {
  if (v is num) return v.toDouble();
  if (v is String) return double.tryParse(v) ?? 0;
  return 0;
}

DateTime? _asDateTime(dynamic v) {
  final millis = _asInt(v);
  if (millis == null || millis <= 0) return null;
  return DateTime.fromMillisecondsSinceEpoch(millis);
}

/// Naive line-level change counter used when the server does not include counts.
({int added, int removed}) countLineChanges(String? before, String? after) {
  if (after == null && before == null) return (added: 0, removed: 0);
  if (before == null) return (added: after!.split('\n').length, removed: 0);
  if (after == null) return (added: 0, removed: before.split('\n').length);

  final bLines = before.split('\n');
  final aLines = after.split('\n');

  // Trim common prefix/suffix, remainder counts as changed lines.
  var p = 0;
  while (p < bLines.length && p < aLines.length && bLines[p] == aLines[p]) {
    p++;
  }
  var s = 0;
  while (s < bLines.length - p &&
      s < aLines.length - p &&
      bLines[bLines.length - 1 - s] == aLines[aLines.length - 1 - s]) {
    s++;
  }
  final removed = bLines.length - p - s;
  final added = aLines.length - p - s;
  return (added: added, removed: removed);
}

/// [ApiException.errorTag] for a health route that answered with something
/// other than a JSON object. An OpenCode 2 host serves its web UI on the
/// OpenCode 1 health path (HTTP 200, text/html), so this is a wrong-generation
/// signal, not a broken server.
const unexpectedHealthShapeTag = 'UnexpectedHealthShape';

class ApiException implements Exception {
  final String message;
  final int? statusCode;
  final String? errorTag;
  final String? requestID;

  ApiException(this.message, {this.statusCode, this.errorTag, this.requestID});

  bool get unauthorized => statusCode == 401 || statusCode == 403;

  bool isPermissionNotFound(String id) =>
      statusCode == 404 &&
      errorTag == 'PermissionNotFoundError' &&
      requestID == id;

  bool isQuestionNotFound(String id) =>
      statusCode == 404 &&
      errorTag == 'QuestionNotFoundError' &&
      requestID == id;

  @override
  String toString() => message;
}
