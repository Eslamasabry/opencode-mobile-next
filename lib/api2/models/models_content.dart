part of '../models.dart';

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
