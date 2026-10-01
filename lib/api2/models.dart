/// Data types for the OpenCode 2 server API (beta-18600 line).
///
/// Hand-written parsers stay tolerant on purpose: unknown enum values map to
/// an `unknown` member, unexpected shapes fall back to safe defaults, and
/// extra fields are ignored — the beta server adds fields between builds.
library;

part 'models/models_content.dart';
part 'models/models_inbox.dart';
part 'models/models_catalog.dart';

int? _asInt(dynamic v) => v is num ? v.toInt() : null;
double? _asDouble(dynamic v) => v is num ? v.toDouble() : null;
String? _asString(dynamic v) => v is String ? v : null;
bool? _asBool(dynamic v) => v is bool ? v : null;

Map<String, dynamic>? _asMap(dynamic v) =>
    v is Map ? Map<String, dynamic>.from(v) : null;

List<dynamic> _asList(dynamic v) => v is List ? v : const [];

List<String> _asStringList(dynamic v) =>
    _asList(v).whereType<String>().toList();

List<T> _mapList<T>(dynamic v, T? Function(Map<String, dynamic>) parse) {
  final out = <T>[];
  for (final item in _asList(v)) {
    final map = _asMap(item);
    if (map == null) continue;
    try {
      final parsed = parse(map);
      if (parsed != null) out.add(parsed);
    } catch (_) {}
  }
  return out;
}

// ---------------- Server / location ----------------

class Api2ServerInfo {
  final List<String> urls;
  Api2ServerInfo({this.urls = const []});

  factory Api2ServerInfo.fromJson(Map<String, dynamic> j) =>
      Api2ServerInfo(urls: _asStringList(j['urls']));
}

class Api2Project {
  final String id;
  final String? directory;
  final String? canonical;
  Api2Project({required this.id, this.directory, this.canonical});

  static Api2Project? fromJson(Map<String, dynamic>? j) {
    final id = _asString(j?['id']);
    if (j == null || id == null) return null;
    return Api2Project(
      id: id,
      directory: _asString(j['directory']),
      canonical: _asString(j['canonical']),
    );
  }
}

/// The `location` object as it appears on events, sessions, and the
/// `GET /api/location` resolution (which adds `project`).
class Api2Location {
  final String? directory;
  final String? workspaceID;
  final Api2Project? project;
  Api2Location({this.directory, this.workspaceID, this.project});

  static Api2Location? fromJson(dynamic v) {
    final j = _asMap(v);
    if (j == null) return null;
    return Api2Location(
      directory: _asString(j['directory']),
      workspaceID: _asString(j['workspaceID']) ?? _asString(j['workspace']),
      project: Api2Project.fromJson(_asMap(j['project'])),
    );
  }

  Map<String, dynamic> toJson() => {
    if (directory != null) 'directory': directory,
    if (workspaceID != null) 'workspaceID': workspaceID,
  };
}

// ---------------- Shared value types ----------------

class Api2Tokens {
  final int input;
  final int output;
  final int reasoning;
  final int cacheRead;
  final int cacheWrite;
  const Api2Tokens({
    this.input = 0,
    this.output = 0,
    this.reasoning = 0,
    this.cacheRead = 0,
    this.cacheWrite = 0,
  });

  factory Api2Tokens.fromJson(dynamic v) {
    final j = _asMap(v);
    if (j == null) return const Api2Tokens();
    final cache = _asMap(j['cache']);
    return Api2Tokens(
      input: _asInt(j['input']) ?? 0,
      output: _asInt(j['output']) ?? 0,
      reasoning: _asInt(j['reasoning']) ?? 0,
      cacheRead: _asInt(cache?['read']) ?? 0,
      cacheWrite: _asInt(cache?['write']) ?? 0,
    );
  }

  int get cache => cacheRead + cacheWrite;
  int get total => input + output + reasoning + cache;
}

class Api2ModelRef {
  final String id;
  final String providerID;
  final String? variant;
  const Api2ModelRef({
    required this.id,
    required this.providerID,
    this.variant,
  });

  static Api2ModelRef? fromJson(dynamic v) {
    final j = _asMap(v);
    final id = _asString(j?['id']);
    final providerID = _asString(j?['providerID']);
    if (j == null || id == null || providerID == null) return null;
    final prefix = '$providerID/';
    return Api2ModelRef(
      id: id.startsWith(prefix) && id.length > prefix.length
          ? id.substring(prefix.length)
          : id,
      providerID: providerID,
      variant: _asString(j['variant']),
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'providerID': providerID,
    if (variant != null) 'variant': variant,
  };

  @override
  String toString() => '$providerID/$id${variant != null ? '#$variant' : ''}';
}

class Api2StructuredError {
  final String? type;
  final String? message;
  final int? status;
  final Map<String, dynamic> raw;
  Api2StructuredError({
    this.type,
    this.message,
    this.status,
    this.raw = const {},
  });

  static Api2StructuredError? fromJson(dynamic v) {
    final j = _asMap(v);
    if (j == null) return null;
    return Api2StructuredError(
      type: _asString(j['type']) ?? _asString(j['name']),
      message: _asString(j['message']),
      status: _asInt(j['status']),
      raw: j,
    );
  }
}

// ---------------- Pagination ----------------

/// `{data: [...], cursor: {previous?, next?}}` — cursors are opaque strings
/// passed back verbatim as `?cursor=`.
class Api2Page<T> {
  final List<T> data;
  final String? previousCursor;
  final String? nextCursor;
  Api2Page({this.data = const [], this.previousCursor, this.nextCursor});

  bool get hasNext => nextCursor != null;
  bool get hasPrevious => previousCursor != null;

  factory Api2Page.fromJson(
    dynamic v,
    T? Function(Map<String, dynamic>) parse,
  ) {
    final j = _asMap(v) ?? const {};
    final cursor = _asMap(j['cursor']);
    return Api2Page(
      data: _mapList(j['data'], parse),
      previousCursor: _asString(cursor?['previous']),
      nextCursor: _asString(cursor?['next']),
    );
  }
}

// ---------------- Sessions ----------------

class Api2SessionTime {
  final int? created;
  final int? updated;
  final int? idle;
  final int? viewed;
  final int? archived;
  Api2SessionTime({
    this.created,
    this.updated,
    this.idle,
    this.viewed,
    this.archived,
  });

  factory Api2SessionTime.fromJson(dynamic v) {
    final j = _asMap(v);
    return Api2SessionTime(
      created: _asInt(j?['created']),
      updated: _asInt(j?['updated']),
      idle: _asInt(j?['idle']),
      viewed: _asInt(j?['viewed']),
      archived: _asInt(j?['archived']),
    );
  }
}

class Api2ForkBoundary {
  final String type;
  final String? messageID;
  Api2ForkBoundary({required this.type, this.messageID});

  static Api2ForkBoundary? fromJson(dynamic v) {
    final j = _asMap(v);
    final type = _asString(j?['type']);
    if (j == null || type == null) return null;
    return Api2ForkBoundary(type: type, messageID: _asString(j['messageID']));
  }

  Map<String, dynamic> toJson() => {
    'type': type,
    if (messageID != null) 'messageID': messageID,
  };
}

class Api2Fork {
  final String? sessionID;
  final Api2ForkBoundary? boundary;
  Api2Fork({this.sessionID, this.boundary});

  static Api2Fork? fromJson(dynamic v) {
    final j = _asMap(v);
    if (j == null) return null;
    return Api2Fork(
      sessionID: _asString(j['sessionID']),
      boundary: Api2ForkBoundary.fromJson(j['boundary']),
    );
  }
}

enum Api2SessionOutcome {
  succeeded,
  failed,
  interrupted,
  unknown;

  static Api2SessionOutcome? parse(dynamic v) => switch (_asString(v)) {
    null => null,
    'succeeded' => succeeded,
    'failed' => failed,
    'interrupted' => interrupted,
    _ => unknown,
  };
}

class Api2SessionRevert {
  final String messageID;
  final String? partID;
  final String? snapshot;
  final List<Map<String, dynamic>>? files;

  Api2SessionRevert({
    required this.messageID,
    this.partID,
    this.snapshot,
    this.files,
  });

  static Api2SessionRevert? fromJson(dynamic value) {
    final json = _asMap(value);
    final messageID = _asString(json?['messageID']);
    if (json == null || messageID == null || messageID.isEmpty) return null;
    return Api2SessionRevert(
      messageID: messageID,
      partID: _asString(json['partID']),
      snapshot: _asString(json['snapshot']),
      files: json['files'] is List
          ? [
              for (final file in json['files'])
                if (file is Map) _asMap(file)!,
            ]
          : null,
    );
  }
}

class Api2Session {
  final String id;
  final String? parentID;
  final Api2Fork? fork;
  final String? projectID;
  final String? agent;
  final Api2ModelRef? model;
  final double cost;
  final Api2Tokens tokens;
  final Api2SessionOutcome? outcome;
  final Api2SessionTime time;
  final String? title;
  final Api2Location? location;
  final String? subpath;
  final bool reverted;
  final Api2SessionRevert? revert;
  final Map<String, dynamic>? metadata;

  Api2Session({
    required this.id,
    this.parentID,
    this.fork,
    this.projectID,
    this.agent,
    this.model,
    this.cost = 0,
    this.tokens = const Api2Tokens(),
    this.outcome,
    Api2SessionTime? time,
    this.title,
    this.location,
    this.subpath,
    this.reverted = false,
    this.revert,
    this.metadata,
  }) : time = time ?? Api2SessionTime();

  static Api2Session? fromJson(Map<String, dynamic> j) {
    final id = _asString(j['id']);
    if (id == null) return null;
    return Api2Session(
      id: id,
      parentID: _asString(j['parentID']),
      fork: Api2Fork.fromJson(j['fork']),
      projectID: _asString(j['projectID']),
      agent: _asString(j['agent']),
      model: Api2ModelRef.fromJson(j['model']),
      cost: _asDouble(j['cost']) ?? 0,
      tokens: Api2Tokens.fromJson(j['tokens']),
      outcome: Api2SessionOutcome.parse(j['outcome']),
      time: Api2SessionTime.fromJson(j['time']),
      title: _asString(j['title']),
      location: Api2Location.fromJson(j['location']),
      subpath: _asString(j['subpath']),
      reverted: j['revert'] != null,
      revert: Api2SessionRevert.fromJson(j['revert']),
      metadata: _asMap(j['metadata']),
    );
  }

  bool get archived => (time.archived ?? 0) > 0;
  String? get directory => location?.directory;
}

// ---------------- File attachments ----------------

class Api2FileAttachment {
  final String? data;
  final String? mime;
  final String? sourceType;
  final String? sourceUri;
  final String? name;
  final String? description;
  Api2FileAttachment({
    this.data,
    this.mime,
    this.sourceType,
    this.sourceUri,
    this.name,
    this.description,
  });

  factory Api2FileAttachment.fromJson(Map<String, dynamic> j) {
    final source = _asMap(j['source']);
    return Api2FileAttachment(
      data: _asString(j['data']),
      mime: _asString(j['mime']),
      sourceType: _asString(source?['type']),
      sourceUri: _asString(source?['uri']),
      name: _asString(j['name']),
      description: _asString(j['description']),
    );
  }
}
