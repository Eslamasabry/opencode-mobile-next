/// Data types for the OpenCode 2 server API (beta-18600 line).
///
/// Hand-written parsers stay tolerant on purpose: unknown enum values map to
/// an `unknown` member, unexpected shapes fall back to safe defaults, and
/// extra fields are ignored — the beta server adds fields between builds.
library;

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

// ---------------- Assistant content ----------------

/// One item of a tool result's `content` array (`text` | `file`).
sealed class Api2ToolResultItem {
  const Api2ToolResultItem();

  static Api2ToolResultItem? fromJson(Map<String, dynamic> j) =>
      switch (_asString(j['type'])) {
        'text' => Api2ToolResultText(_asString(j['text']) ?? ''),
        'file' => Api2ToolResultFile(
          uri: _asString(j['uri']) ?? '',
          mime: _asString(j['mime']),
          name: _asString(j['name']),
        ),
        _ => Api2ToolResultUnknown(j),
      };
}

class Api2ToolResultText extends Api2ToolResultItem {
  final String text;
  const Api2ToolResultText(this.text);
}

class Api2ToolResultFile extends Api2ToolResultItem {
  final String uri;
  final String? mime;
  final String? name;
  const Api2ToolResultFile({required this.uri, this.mime, this.name});
}

class Api2ToolResultUnknown extends Api2ToolResultItem {
  final Map<String, dynamic> raw;
  const Api2ToolResultUnknown(this.raw);
}

/// Tool call state, discriminated by `status`:
/// `streaming` → `running` → `completed` | `error`.
sealed class Api2ToolState {
  const Api2ToolState();

  Map<String, dynamic>? get input => null;
  Map<String, dynamic>? get metadata => null;
  List<Api2ToolResultItem> get content => const [];

  factory Api2ToolState.fromJson(dynamic v) {
    final j = _asMap(v) ?? const {};
    return switch (_asString(j['status'])) {
      'streaming' => Api2ToolStreaming(rawInput: _asString(j['input']) ?? ''),
      'running' => Api2ToolRunning(
        input: _asMap(j['input']) ?? const {},
        metadata: _asMap(j['metadata']),
      ),
      'completed' => Api2ToolCompleted(
        input: _asMap(j['input']) ?? const {},
        content: _mapList(j['content'], Api2ToolResultItem.fromJson),
        metadata: _asMap(j['metadata']),
      ),
      'error' => Api2ToolError(
        input: _asMap(j['input']) ?? const {},
        error: Api2StructuredError.fromJson(j['error']),
        content: _mapList(j['content'], Api2ToolResultItem.fromJson),
        metadata: _asMap(j['metadata']),
      ),
      _ => Api2ToolStateUnknown(j),
    };
  }

  String get textOutput =>
      content.whereType<Api2ToolResultText>().map((c) => c.text).join('\n');
}

class Api2ToolStreaming extends Api2ToolState {
  final String rawInput;
  const Api2ToolStreaming({required this.rawInput});
}

class Api2ToolRunning extends Api2ToolState {
  @override
  final Map<String, dynamic> input;
  @override
  final Map<String, dynamic>? metadata;
  const Api2ToolRunning({required this.input, this.metadata});
}

class Api2ToolCompleted extends Api2ToolState {
  @override
  final Map<String, dynamic> input;
  @override
  final List<Api2ToolResultItem> content;
  @override
  final Map<String, dynamic>? metadata;
  const Api2ToolCompleted({
    required this.input,
    this.content = const [],
    this.metadata,
  });
}

class Api2ToolError extends Api2ToolState {
  @override
  final Map<String, dynamic> input;
  final Api2StructuredError? error;
  @override
  final List<Api2ToolResultItem> content;
  @override
  final Map<String, dynamic>? metadata;
  const Api2ToolError({
    required this.input,
    this.error,
    this.content = const [],
    this.metadata,
  });
}

class Api2ToolStateUnknown extends Api2ToolState {
  final Map<String, dynamic> raw;
  const Api2ToolStateUnknown(this.raw);
}

class Api2ContentTime {
  final int? created;
  final int? ran;
  final int? completed;

  /// When the server pruned this item's output from the context (newer v2
  /// builds only; absent from the beta-18600 contract).
  final int? pruned;
  Api2ContentTime({this.created, this.ran, this.completed, this.pruned});

  factory Api2ContentTime.fromJson(dynamic v) {
    final j = _asMap(v);
    return Api2ContentTime(
      created: _asInt(j?['created']),
      ran: _asInt(j?['ran']),
      completed: _asInt(j?['completed']),
      pruned: _asInt(j?['pruned']),
    );
  }
}

/// Assistant message content item (`text` | `reasoning` | `tool`).
sealed class Api2AssistantContent {
  const Api2AssistantContent();

  static Api2AssistantContent fromJson(Map<String, dynamic> j) =>
      switch (_asString(j['type'])) {
        'text' => Api2TextContent(
          text: _asString(j['text']) ?? '',
          state: _asMap(j['state']),
        ),
        'reasoning' => Api2ReasoningContent(
          text: _asString(j['text']) ?? '',
          state: _asMap(j['state']),
          time: Api2ContentTime.fromJson(j['time']),
        ),
        'tool' => Api2ToolCallContent(
          id: _asString(j['id']) ?? '',
          name: _asString(j['name']) ?? '',
          executed: _asBool(j['executed']),
          state: Api2ToolState.fromJson(j['state']),
          time: Api2ContentTime.fromJson(j['time']),
        ),
        _ => Api2UnknownContent(j),
      };
}

class Api2TextContent extends Api2AssistantContent {
  final String text;
  final Map<String, dynamic>? state;
  const Api2TextContent({required this.text, this.state});
}

class Api2ReasoningContent extends Api2AssistantContent {
  final String text;
  final Map<String, dynamic>? state;
  final Api2ContentTime? time;
  const Api2ReasoningContent({required this.text, this.state, this.time});
}

class Api2ToolCallContent extends Api2AssistantContent {
  final String id;
  final String name;
  final bool? executed;
  final Api2ToolState state;
  final Api2ContentTime? time;
  const Api2ToolCallContent({
    required this.id,
    required this.name,
    this.executed,
    required this.state,
    this.time,
  });
}

class Api2UnknownContent extends Api2AssistantContent {
  final Map<String, dynamic> raw;
  const Api2UnknownContent(this.raw);
}

// ---------------- Messages ----------------

class Api2MessageTime {
  final int? created;
  final int? streamed;
  final int? completed;
  Api2MessageTime({this.created, this.streamed, this.completed});

  factory Api2MessageTime.fromJson(dynamic v) {
    final j = _asMap(v);
    return Api2MessageTime(
      created: _asInt(j?['created']),
      streamed: _asInt(j?['streamed']),
      completed: _asInt(j?['completed']),
    );
  }
}

/// The 10-variant session message union, plus an unknown fallback.
sealed class Api2Message {
  final String id;
  final Api2MessageTime time;
  final Map<String, dynamic>? metadata;
  final Map<String, dynamic> raw;
  const Api2Message({
    required this.id,
    required this.time,
    this.metadata,
    this.raw = const {},
  });

  String get type => _asString(raw['type']) ?? 'unknown';

  static Api2Message? fromJson(Map<String, dynamic> j) {
    final id = _asString(j['id']);
    if (id == null) return null;
    final time = Api2MessageTime.fromJson(j['time']);
    final metadata = _asMap(j['metadata']);
    switch (_asString(j['type'])) {
      case 'user':
        return Api2UserMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
          text: _asString(j['text']) ?? '',
          files: _mapList(j['files'], Api2FileAttachment.fromJson),
        );
      case 'assistant':
        return Api2AssistantMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
          agent: _asString(j['agent']),
          model: Api2ModelRef.fromJson(j['model']),
          content: _mapList(j['content'], Api2AssistantContent.fromJson),
          finish: _asString(j['finish']),
          rawFinish: _asString(j['rawFinish']),
          cost: _asDouble(j['cost']),
          tokens: j['tokens'] != null ? Api2Tokens.fromJson(j['tokens']) : null,
          error: Api2StructuredError.fromJson(j['error']),
        );
      case 'synthetic':
        return Api2SyntheticMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
          text: _asString(j['text']) ?? '',
          description: _asString(j['description']),
        );
      case 'system':
        return Api2SystemMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
          text: _asString(j['text']) ?? '',
          description: _asString(j['description']),
        );
      case 'skill':
        return Api2SkillMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
          skill: _asString(j['skill']) ?? '',
          name: _asString(j['name']) ?? '',
          text: _asString(j['text']) ?? '',
        );
      case 'shell':
        final output = _asMap(j['output']);
        return Api2ShellMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
          shellID: _asString(j['shellID']),
          command: _asString(j['command']) ?? '',
          status: _asString(j['status']) ?? '',
          exit: _asInt(j['exit']),
          output: _asString(output?['output']),
          outputTruncated: _asBool(output?['truncated']) ?? false,
        );
      case 'agent-switched':
        return Api2AgentSwitchedMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
          agent: _asString(j['agent']) ?? '',
          previous: _asString(j['previous']),
        );
      case 'model-switched':
        return Api2ModelSwitchedMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
          model: Api2ModelRef.fromJson(j['model']),
          previous: Api2ModelRef.fromJson(j['previous']),
        );
      case 'location-switched':
        return Api2LocationSwitchedMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
          location: Api2Location.fromJson(j['location']),
          projectID: _asString(j['projectID']),
          subpath: _asString(j['subpath']),
        );
      case 'compaction':
        return Api2CompactionMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
          status: _asString(j['status']) ?? '',
          reason: _asString(j['reason']),
          summary: _asString(j['summary']),
          error: Api2StructuredError.fromJson(j['error']),
        );
      default:
        return Api2UnknownMessage(
          id: id,
          time: time,
          metadata: metadata,
          raw: j,
        );
    }
  }
}

class Api2UserMessage extends Api2Message {
  final String text;
  final List<Api2FileAttachment> files;
  const Api2UserMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
    required this.text,
    this.files = const [],
  });
}

class Api2AssistantMessage extends Api2Message {
  final String? agent;
  final Api2ModelRef? model;
  final List<Api2AssistantContent> content;
  final String? finish;
  final String? rawFinish;
  final double? cost;
  final Api2Tokens? tokens;
  final Api2StructuredError? error;
  const Api2AssistantMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
    this.agent,
    this.model,
    this.content = const [],
    this.finish,
    this.rawFinish,
    this.cost,
    this.tokens,
    this.error,
  });

  bool get completed => time.completed != null;

  String get text => content
      .whereType<Api2TextContent>()
      .map((c) => c.text)
      .where((t) => t.isNotEmpty)
      .join('\n');
}

class Api2SyntheticMessage extends Api2Message {
  final String text;
  final String? description;
  const Api2SyntheticMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
    required this.text,
    this.description,
  });
}

class Api2SystemMessage extends Api2Message {
  final String text;
  final String? description;
  const Api2SystemMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
    required this.text,
    this.description,
  });
}

class Api2SkillMessage extends Api2Message {
  final String skill;
  final String name;
  final String text;
  const Api2SkillMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
    required this.skill,
    required this.name,
    required this.text,
  });
}

class Api2ShellMessage extends Api2Message {
  final String? shellID;
  final String command;
  final String status;
  final int? exit;
  final String? output;
  final bool outputTruncated;
  const Api2ShellMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
    this.shellID,
    required this.command,
    required this.status,
    this.exit,
    this.output,
    this.outputTruncated = false,
  });
}

class Api2AgentSwitchedMessage extends Api2Message {
  final String agent;
  final String? previous;
  const Api2AgentSwitchedMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
    required this.agent,
    this.previous,
  });
}

class Api2ModelSwitchedMessage extends Api2Message {
  final Api2ModelRef? model;
  final Api2ModelRef? previous;
  const Api2ModelSwitchedMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
    this.model,
    this.previous,
  });
}

class Api2LocationSwitchedMessage extends Api2Message {
  final Api2Location? location;
  final String? projectID;
  final String? subpath;
  const Api2LocationSwitchedMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
    this.location,
    this.projectID,
    this.subpath,
  });
}

class Api2CompactionMessage extends Api2Message {
  final String status;
  final String? reason;
  final String? summary;
  final Api2StructuredError? error;
  const Api2CompactionMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
    required this.status,
    this.reason,
    this.summary,
    this.error,
  });
}

class Api2UnknownMessage extends Api2Message {
  const Api2UnknownMessage({
    required super.id,
    required super.time,
    super.metadata,
    super.raw,
  });
}

// ---------------- Inbox ----------------

enum Api2Delivery {
  steer,
  queue,
  unknown;

  static Api2Delivery? parse(dynamic v) => switch (_asString(v)) {
    null => null,
    'steer' => steer,
    'queue' => queue,
    _ => unknown,
  };

  String get wire => name;
}

/// Receipt returned by `POST /api/session/{id}/prompt` and the entries of
/// `GET /api/session/{id}/inbox`.
class Api2InboxItem {
  final String id;
  final String? sessionID;
  final int? timeCreated;
  final String type;
  final Map<String, dynamic> payload;
  final Api2Delivery? delivery;
  Api2InboxItem({
    required this.id,
    this.sessionID,
    this.timeCreated,
    required this.type,
    this.payload = const {},
    this.delivery,
  });

  static Api2InboxItem? fromJson(Map<String, dynamic> j) {
    final id = _asString(j['id']);
    if (id == null) return null;
    return Api2InboxItem(
      id: id,
      sessionID: _asString(j['sessionID']),
      timeCreated: _asInt(j['timeCreated']),
      type: _asString(j['type']) ?? 'unknown',
      payload: _asMap(j['payload']) ?? const {},
      delivery: Api2Delivery.parse(j['delivery']),
    );
  }

  String? get promptText => _asString(payload['text']);
}

// ---------------- Permissions ----------------

enum Api2PermissionReply {
  once('once'),
  always('always'),
  reject('reject');

  final String wire;
  const Api2PermissionReply(this.wire);
}

enum Api2PermissionEffect {
  allow,
  deny,
  ask,
  unknown;

  static Api2PermissionEffect? parse(dynamic v) => switch (_asString(v)) {
    null => null,
    'allow' => allow,
    'deny' => deny,
    'ask' => ask,
    _ => unknown,
  };
}

class Api2PermissionSource {
  final String? type;
  final String? messageID;
  final String? id;
  Api2PermissionSource({this.type, this.messageID, this.id});

  static Api2PermissionSource? fromJson(dynamic v) {
    final j = _asMap(v);
    if (j == null) return null;
    return Api2PermissionSource(
      type: _asString(j['type']),
      messageID: _asString(j['messageID']),
      id: _asString(j['id']),
    );
  }
}

class Api2PermissionRequest {
  final String id;
  final String? sessionID;
  final String action;
  final List<String> resources;
  final List<String> save;
  final Map<String, dynamic>? metadata;
  final Api2PermissionSource? source;
  final String? message;
  Api2PermissionRequest({
    required this.id,
    this.sessionID,
    required this.action,
    this.resources = const [],
    this.save = const [],
    this.metadata,
    this.source,
    this.message,
  });

  static Api2PermissionRequest? fromJson(Map<String, dynamic> j) {
    final id = _asString(j['id']);
    if (id == null) return null;
    return Api2PermissionRequest(
      id: id,
      sessionID: _asString(j['sessionID']),
      action: _asString(j['action']) ?? '',
      resources: _asStringList(j['resources']),
      save: _asStringList(j['save']),
      metadata: _asMap(j['metadata']),
      source: Api2PermissionSource.fromJson(j['source']),
      message: _asString(j['message']),
    );
  }
}

class Api2PermissionRule {
  final String? action;
  final String? resource;
  final Api2PermissionEffect? effect;
  Api2PermissionRule({this.action, this.resource, this.effect});

  factory Api2PermissionRule.fromJson(Map<String, dynamic> j) =>
      Api2PermissionRule(
        action: _asString(j['action']),
        resource: _asString(j['resource']),
        effect: Api2PermissionEffect.parse(j['effect']),
      );
}

class Api2SavedPermission {
  final String id;
  final String? projectID;
  final String? action;
  final String? resource;
  Api2SavedPermission({
    required this.id,
    this.projectID,
    this.action,
    this.resource,
  });

  static Api2SavedPermission? fromJson(Map<String, dynamic> j) {
    final id = _asString(j['id']);
    if (id == null) return null;
    return Api2SavedPermission(
      id: id,
      projectID: _asString(j['projectID']),
      action: _asString(j['action']),
      resource: _asString(j['resource']),
    );
  }
}

// ---------------- Forms ----------------

enum Api2FormFieldType {
  string,
  number,
  integer,
  boolean,
  multiselect,
  external,
  unknown;

  static Api2FormFieldType parse(dynamic v) => switch (_asString(v)) {
    'string' => string,
    'number' => number,
    'integer' => integer,
    'boolean' => boolean,
    'multiselect' => multiselect,
    'external' => external,
    _ => unknown,
  };
}

class Api2FormOption {
  final String value;
  final String? label;
  final String? description;
  Api2FormOption({required this.value, this.label, this.description});

  static Api2FormOption? fromJson(Map<String, dynamic> j) {
    final value = _asString(j['value']);
    if (value == null) return null;
    return Api2FormOption(
      value: value,
      label: _asString(j['label']),
      description: _asString(j['description']),
    );
  }
}

class Api2FormCondition {
  final String key;
  final String op;
  final dynamic value;
  Api2FormCondition({required this.key, required this.op, this.value});

  static Api2FormCondition? fromJson(Map<String, dynamic> j) {
    final key = _asString(j['key']);
    if (key == null) return null;
    return Api2FormCondition(
      key: key,
      op: _asString(j['op']) ?? 'eq',
      value: j['value'],
    );
  }

  /// `eq` against a multiselect answer means "includes".
  bool holds(Map<String, dynamic> answers) {
    if (!answers.containsKey(key)) return false;
    final current = answers[key];
    final matches = current is List
        ? current.contains(value)
        : current == value;
    return op == 'neq' ? !matches : matches;
  }
}

class Api2FormField {
  final String key;
  final Api2FormFieldType type;
  final String? title;
  final String? description;
  final bool required;
  final List<Api2FormCondition> when;
  final String? format;
  final int? minLength;
  final int? maxLength;
  final String? pattern;
  final String? placeholder;
  final dynamic defaultValue;
  final List<Api2FormOption> options;
  final bool custom;
  final num? minimum;
  final num? maximum;
  final int? minItems;
  final int? maxItems;
  final String? url;
  Api2FormField({
    required this.key,
    required this.type,
    this.title,
    this.description,
    this.required = false,
    this.when = const [],
    this.format,
    this.minLength,
    this.maxLength,
    this.pattern,
    this.placeholder,
    this.defaultValue,
    this.options = const [],
    this.custom = false,
    this.minimum,
    this.maximum,
    this.minItems,
    this.maxItems,
    this.url,
  });

  static Api2FormField? fromJson(Map<String, dynamic> j) {
    final key = _asString(j['key']);
    if (key == null) return null;
    return Api2FormField(
      key: key,
      type: Api2FormFieldType.parse(j['type']),
      title: _asString(j['title']),
      description: _asString(j['description']),
      required: _asBool(j['required']) ?? false,
      when: _mapList(j['when'], Api2FormCondition.fromJson),
      format: _asString(j['format']),
      minLength: _asInt(j['minLength']),
      maxLength: _asInt(j['maxLength']),
      pattern: _asString(j['pattern']),
      placeholder: _asString(j['placeholder']),
      defaultValue: j['default'],
      options: _mapList(j['options'], Api2FormOption.fromJson),
      custom: _asBool(j['custom']) ?? false,
      minimum: j['minimum'] is num ? j['minimum'] as num : null,
      maximum: j['maximum'] is num ? j['maximum'] as num : null,
      minItems: _asInt(j['minItems']),
      maxItems: _asInt(j['maxItems']),
      url: _asString(j['url']),
    );
  }

  /// All `when` conditions must hold for the field to be active.
  bool activeFor(Map<String, dynamic> answers) =>
      when.every((c) => c.holds(answers));
}

class Api2FormInfo {
  final String id;

  /// Session id, or the sentinel `"global"` for MCP elicitation.
  final String sessionID;
  final String? title;
  final Map<String, dynamic>? metadata;
  final List<Api2FormField> fields;
  Api2FormInfo({
    required this.id,
    required this.sessionID,
    this.title,
    this.metadata,
    this.fields = const [],
  });

  static Api2FormInfo? fromJson(Map<String, dynamic> j) {
    final id = _asString(j['id']);
    if (id == null) return null;
    return Api2FormInfo(
      id: id,
      sessionID: _asString(j['sessionID']) ?? '',
      title: _asString(j['title']),
      metadata: _asMap(j['metadata']),
      fields: _mapList(j['fields'], Api2FormField.fromJson),
    );
  }
}

enum Api2FormStatus {
  pending,
  answered,
  cancelled,
  unknown;

  static Api2FormStatus parse(dynamic v) => switch (_asString(v)) {
    'pending' => pending,
    'answered' => answered,
    'cancelled' => cancelled,
    _ => unknown,
  };
}

class Api2FormState {
  final Api2FormStatus status;
  final Map<String, dynamic>? answer;
  Api2FormState({required this.status, this.answer});

  factory Api2FormState.fromJson(dynamic v) {
    final j = _asMap(v) ?? const {};
    return Api2FormState(
      status: Api2FormStatus.parse(j['status']),
      answer: _asMap(j['answer']),
    );
  }
}

// ---------------- Models / providers / agents ----------------

class Api2ModelLimit {
  final int? context;
  final int? input;
  final int? output;
  Api2ModelLimit({this.context, this.input, this.output});

  factory Api2ModelLimit.fromJson(dynamic v) {
    final j = _asMap(v);
    return Api2ModelLimit(
      context: _asInt(j?['context']),
      input: _asInt(j?['input']),
      output: _asInt(j?['output']),
    );
  }
}

class Api2ModelCapabilities {
  final bool tools;
  final List<String> input;
  final List<String> output;
  Api2ModelCapabilities({
    this.tools = false,
    this.input = const [],
    this.output = const [],
  });

  factory Api2ModelCapabilities.fromJson(dynamic v) {
    final j = _asMap(v);
    return Api2ModelCapabilities(
      tools: _asBool(j?['tools']) ?? false,
      input: _asStringList(j?['input']),
      output: _asStringList(j?['output']),
    );
  }
}

/// One `Model.Cost` entry: USD per million tokens (`Money.USDPerMillionTokens`)
/// for input/output and cache read/write. [tierSize] is set on context-tier
/// overrides (`tier: {type: 'context', size}`); the base price has none.
class Api2ModelCost {
  final double input;
  final double output;
  final double cacheRead;
  final double cacheWrite;
  final int? tierSize;
  const Api2ModelCost({
    this.input = 0,
    this.output = 0,
    this.cacheRead = 0,
    this.cacheWrite = 0,
    this.tierSize,
  });

  static Api2ModelCost? fromJson(dynamic v) {
    final j = _asMap(v);
    if (j == null) return null;
    final cache = _asMap(j['cache']);
    return Api2ModelCost(
      input: _asDouble(j['input']) ?? 0,
      output: _asDouble(j['output']) ?? 0,
      cacheRead: _asDouble(cache?['read']) ?? 0,
      cacheWrite: _asDouble(cache?['write']) ?? 0,
      tierSize: _asInt(_asMap(j['tier'])?['size']),
    );
  }
}

class Api2ModelInfo {
  final String id;
  final String? modelID;
  final String? providerID;
  final String? family;
  final String? name;
  final String? status;
  final bool enabled;
  final Api2ModelCapabilities capabilities;
  final List<String> variants;
  final Api2ModelLimit limit;
  final int? released;

  /// Price list as the server sends it (base entry first, then context
  /// tiers). Empty when the server omitted `cost`.
  final List<Api2ModelCost> cost;

  /// The full wire object, preserved for consumers (like the domain gateway
  /// mappers) that need fields this projection does not model — e.g. variant
  /// settings such as `reasoningEffort`.
  final Map<String, dynamic> raw;

  Api2ModelInfo({
    required this.id,
    this.modelID,
    this.providerID,
    this.family,
    this.name,
    this.status,
    this.enabled = true,
    Api2ModelCapabilities? capabilities,
    this.variants = const [],
    Api2ModelLimit? limit,
    this.released,
    this.cost = const [],
    this.raw = const {},
  }) : capabilities = capabilities ?? Api2ModelCapabilities(),
       limit = limit ?? Api2ModelLimit();

  /// Base (non-tiered) price, or the first entry when every entry is tiered.
  Api2ModelCost? get baseCost => cost.isEmpty
      ? null
      : cost.firstWhere((c) => c.tierSize == null, orElse: () => cost.first);

  static Api2ModelInfo? fromJson(Map<String, dynamic> j) {
    final id = _asString(j['id']);
    if (id == null) return null;
    final providerID = _asString(j['providerID']);
    var modelID = _asString(j['modelID']);
    // Older betas list models under a composite "provider/model" id without
    // a separate modelID; peel the provider prefix so prompts get the bare id.
    if (modelID == null &&
        providerID != null &&
        id.startsWith('$providerID/')) {
      modelID = id.substring(providerID.length + 1);
    }
    return Api2ModelInfo(
      id: id,
      modelID: modelID,
      providerID: providerID,
      family: _asString(j['family']),
      name: _asString(j['name']),
      status: _asString(j['status']),
      enabled: _asBool(j['enabled']) ?? true,
      capabilities: Api2ModelCapabilities.fromJson(j['capabilities']),
      variants: _mapList(j['variants'], (v) => _asString(v['id'])),
      limit: Api2ModelLimit.fromJson(j['limit']),
      released: _asInt(_asMap(j['time'])?['released']),
      cost: j['cost'] is List
          ? _mapList(j['cost'], Api2ModelCost.fromJson)
          : [?Api2ModelCost.fromJson(j['cost'])],
      raw: j,
    );
  }

  Api2ModelRef ref({String? variant}) => Api2ModelRef(
    id: modelID ?? id,
    providerID: providerID ?? '',
    variant: variant,
  );
}

class Api2ProviderInfo {
  final String id;
  final String? integrationID;
  final String? name;
  final String? activation;
  final String? package;
  Api2ProviderInfo({
    required this.id,
    this.integrationID,
    this.name,
    this.activation,
    this.package,
  });

  static Api2ProviderInfo? fromJson(Map<String, dynamic> j) {
    final id = _asString(j['id']);
    if (id == null) return null;
    return Api2ProviderInfo(
      id: id,
      integrationID: _asString(j['integrationID']),
      name: _asString(j['name']),
      activation: _asString(j['activation']),
      package: _asString(j['package']),
    );
  }
}

class Api2AgentInfo {
  final String id;
  final String name;
  final String? description;
  final String? mode;
  final bool hidden;
  final String? color;
  final Api2ModelRef? model;
  final List<Api2PermissionRule> permissions;
  Api2AgentInfo({
    required this.id,
    required this.name,
    this.description,
    this.mode,
    this.hidden = false,
    this.color,
    this.model,
    this.permissions = const [],
  });

  static Api2AgentInfo? fromJson(Map<String, dynamic> j) {
    final id = _asString(j['id']) ?? _asString(j['name']);
    if (id == null) return null;
    return Api2AgentInfo(
      id: id,
      name: _asString(j['name']) ?? id,
      description: _asString(j['description']),
      mode: _asString(j['mode']),
      hidden: _asBool(j['hidden']) ?? false,
      color: _asString(j['color']),
      model: Api2ModelRef.fromJson(j['model']),
      permissions: _mapList(j['permissions'], Api2PermissionRule.fromJson),
    );
  }

  bool get selectable => !hidden && mode != 'subagent';
}

// ---------------- Commands / skills / config / fs ----------------

class Api2Command {
  final String name;
  final String? description;
  Api2Command({required this.name, this.description});

  static Api2Command? fromJson(Map<String, dynamic> j) {
    final name = _asString(j['name']);
    if (name == null) return null;
    return Api2Command(name: name, description: _asString(j['description']));
  }
}

class Api2Skill {
  final String id;
  final String? name;
  final String? description;
  final String? location;
  final String? content;
  final bool slash;
  Api2Skill({
    required this.id,
    this.name,
    this.description,
    this.location,
    this.content,
    this.slash = false,
  });

  static Api2Skill? fromJson(Map<String, dynamic> j) {
    final id = _asString(j['id']) ?? _asString(j['name']);
    if (id == null) return null;
    return Api2Skill(
      id: id,
      name: _asString(j['name']),
      description: _asString(j['description']),
      location: _asString(j['location']),
      content: _asString(j['content']),
      slash: _asBool(j['slash']) ?? false,
    );
  }
}

/// One entry of the priority-ordered `GET /api/config` list.
class Api2ConfigEntry {
  final String type;
  final String? path;
  final Map<String, dynamic> info;
  Api2ConfigEntry({required this.type, this.path, this.info = const {}});

  static Api2ConfigEntry? fromJson(Map<String, dynamic> j) {
    final type = _asString(j['type']);
    if (type == null) return null;
    return Api2ConfigEntry(
      type: type,
      path: _asString(j['path']),
      info: _asMap(j['info']) ?? const {},
    );
  }
}

class Api2FsEntry {
  final String path;
  final String type;
  Api2FsEntry({required this.path, required this.type});

  static Api2FsEntry? fromJson(Map<String, dynamic> j) {
    final path = _asString(j['path']);
    if (path == null) return null;
    return Api2FsEntry(path: path, type: _asString(j['type']) ?? 'file');
  }

  bool get isDirectory => type == 'directory';
}

// ---------------- Prompt inputs ----------------

class Api2Mention {
  final int start;
  final int end;
  final String text;
  const Api2Mention({
    required this.start,
    required this.end,
    required this.text,
  });

  Map<String, dynamic> toJson() => {'start': start, 'end': end, 'text': text};
}

/// Prompt attachment: a `data:<mime>;base64,...` or `file:///abs/path` URI.
class Api2PromptFile {
  final String uri;
  final String? name;
  final String? description;
  final Api2Mention? mention;
  const Api2PromptFile({
    required this.uri,
    this.name,
    this.description,
    this.mention,
  });

  Map<String, dynamic> toJson() => {
    'uri': uri,
    if (name != null) 'name': name,
    if (description != null) 'description': description,
    if (mention != null) 'mention': mention!.toJson(),
  };
}

class Api2PromptAgentMention {
  final String name;
  final Api2Mention? mention;
  const Api2PromptAgentMention({required this.name, this.mention});

  Map<String, dynamic> toJson() => {
    'name': name,
    if (mention != null) 'mention': mention!.toJson(),
  };
}

class Api2PromptSkillMention {
  final String id;
  final Api2Mention? mention;
  const Api2PromptSkillMention({required this.id, this.mention});

  Map<String, dynamic> toJson() => {
    'id': id,
    if (mention != null) 'mention': mention!.toJson(),
  };
}
