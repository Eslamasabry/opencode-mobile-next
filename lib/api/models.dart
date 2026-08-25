import 'dart:convert';
import 'dart:typed_data';

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
  SessionTime({this.created, this.updated, this.archived});

  factory SessionTime.fromJson(dynamic v) {
    if (v is Map<String, dynamic>) {
      return SessionTime(
        created: _asInt(v['created']),
        updated: _asInt(v['updated']),
        archived: _asInt(v['archived']),
      );
    }
    return SessionTime();
  }
}

class Session {
  final String id;
  final String? title;
  final String? parentID;
  final String? directory;
  final bool reverted;
  final String? shareUrl;
  final SessionTime? time;
  Session({
    required this.id,
    this.title,
    this.parentID,
    this.directory,
    this.reverted = false,
    this.shareUrl,
    this.time,
  });

  factory Session.fromJson(Map<String, dynamic> j) => Session(
    id: j['id'] as String,
    title: j['title'] as String?,
    parentID: j['parentID'] as String?,
    directory: j['directory'] as String?,
    reverted: j['revert'] != null,
    shareUrl: j['share'] is Map ? (j['share'] as Map)['url']?.toString() : null,
    time: SessionTime.fromJson(j['time']),
  );

  bool get archived => time?.archived != null;
}

// ---------------- Messages & parts ----------------

class MsgTime {
  final int? created;
  final int? completed;
  MsgTime({this.created, this.completed});

  factory MsgTime.fromJson(dynamic v) {
    if (v is Map<String, dynamic>) {
      return MsgTime(
        created: _asInt(v['created']),
        completed: _asInt(v['completed']),
      );
    }
    return MsgTime();
  }

  bool get isDone => completed != null && completed! > 0;
}

class Tokens {
  final int input;
  final int output;
  final int reasoning;
  Tokens({this.input = 0, this.output = 0, this.reasoning = 0});

  factory Tokens.fromJson(dynamic v) {
    if (v is! Map<String, dynamic>) return Tokens();
    return Tokens(
      input: _asInt(v['input']) ?? 0,
      output: _asInt(v['output']) ?? 0,
      reasoning: _asInt(v['reasoning']) ?? 0,
    );
  }

  int get total => input + output + reasoning;
}

class MessageInfo {
  final String id;
  final String sessionID;
  final String role; // "user" | "assistant"
  final String? agent;
  final String? providerID;
  final String? modelID;
  final double cost;
  final Tokens tokens;
  final MsgTime? time;
  final String? errorText;

  MessageInfo({
    required this.id,
    required this.sessionID,
    required this.role,
    this.agent,
    this.providerID,
    this.modelID,
    this.cost = 0,
    Tokens? tokens,
    this.time,
    this.errorText,
  }) : tokens = tokens ?? Tokens();

  factory MessageInfo.fromJson(Map<String, dynamic> j) {
    final err = j['error'];
    String? errText;
    if (err is Map<String, dynamic>) {
      final data = err['data'];
      final nestedMessage = data is Map ? data['message'] : null;
      errText = (err['message'] ?? nestedMessage ?? err['name'])?.toString();
    } else if (err is String) {
      errText = err;
    }
    return MessageInfo(
      id: j['id'] as String,
      sessionID: (j['sessionID'] as String?) ?? '',
      role: (j['role'] as String?) ?? 'user',
      agent: j['agent'] as String?,
      providerID: j['providerID'] as String?,
      modelID: j['modelID'] as String?,
      cost: _asDouble(j['cost']),
      tokens: Tokens.fromJson(j['tokens']),
      time: MsgTime.fromJson(j['time']),
      errorText: errText,
    );
  }
}

/// Tool state extracted from a tool part's `state` object.
class ToolState {
  final String status; // pending | running | completed | error
  final String? title;
  final String? inputJson;
  final String? output;
  final Map<String, dynamic>? metadata;

  ToolState({
    required this.status,
    this.title,
    this.inputJson,
    this.output,
    this.metadata,
  });

  static String _pretty(dynamic v) {
    if (v == null) return '';
    if (v is String) return v;
    try {
      if (v is Map || v is List) {
        return const JsonEncoder.withIndent('  ').convert(v);
      }
      return v.toString().trim();
    } catch (_) {
      return '';
    }
  }

  factory ToolState.fromJson(dynamic v) {
    if (v is! Map<String, dynamic>) {
      return ToolState(status: 'pending');
    }
    final rawStatus = (v['status'] ?? v['type'] ?? 'pending').toString();
    final status = switch (rawStatus) {
      'running' || 'pending' || 'completed' || 'error' => rawStatus,
      _ => 'running',
    };
    final meta = v['metadata'] is Map<String, dynamic>
        ? v['metadata'] as Map<String, dynamic>
        : null;
    final input = status == 'pending' ? _pretty(v['raw']) : _pretty(v['input']);
    final output = switch (status) {
      'completed' => _pretty(v['output']),
      'error' => _pretty(v['error']),
      _ => '',
    };
    return ToolState(
      status: status,
      title: v['title']?.toString(),
      inputJson: input,
      output: output,
      metadata: meta,
    );
  }
}

/// One part of a message. The opencode part union is loose; we keep what we render.
class Part {
  final String? id;
  final String
  type; // text | reasoning | tool | file | step-start | step-finish | snapshot
  final String text;
  final String? messageID;
  final String? callID;
  final String? toolName;
  final ToolState toolState;
  final String? mime;
  final String? filename;
  final String? url;
  final bool synthetic;

  Part({
    this.id,
    required this.type,
    this.text = '',
    this.messageID,
    this.callID,
    this.toolName,
    ToolState? toolState,
    this.mime,
    this.filename,
    this.url,
    this.synthetic = false,
  }) : toolState = toolState ?? ToolState(status: 'pending');

  bool get isRenderable =>
      !synthetic &&
      ((type == 'text' && text.trim().isNotEmpty) ||
          (type == 'reasoning' && text.trim().isNotEmpty) ||
          type == 'tool' ||
          type == 'file');

  factory Part.fromJson(Map<String, dynamic> j) {
    return Part(
      id: (j['id'] ?? j['callID'])?.toString(),
      type: (j['type'] as String?) ?? 'text',
      text: (j['text'] as String?) ?? '',
      messageID: j['messageID']?.toString(),
      callID: j['callID']?.toString(),
      toolName: j['tool']?.toString(),
      toolState: ToolState.fromJson(j['state']),
      mime: j['mime']?.toString(),
      filename: j['filename']?.toString(),
      url: j['url']?.toString(),
      synthetic: (j['synthetic'] as bool?) ?? false,
    );
  }
}

class MessageWithParts {
  final MessageInfo info;
  List<Part> parts;
  MessageWithParts({required this.info, List<Part>? parts})
    : parts = parts ?? [];
}

// ---------------- Requests ----------------

class ModelRef {
  final String providerID;
  final String modelID;
  ModelRef({required this.providerID, required this.modelID});

  Map<String, dynamic> toJson() => {
    'providerID': providerID,
    'modelID': modelID,
  };

  String get wireName => '$providerID/$modelID';
}

class PromptAttachment {
  final String mime;
  final String filename;
  final String url;

  const PromptAttachment({
    required this.mime,
    required this.filename,
    required this.url,
  });

  Map<String, dynamic> toJson() => {
    'type': 'file',
    'mime': mime,
    'filename': filename,
    'url': url,
  };
}

Map<String, dynamic> promptRequestBody({
  required String text,
  ModelRef? model,
  String? agent,
  String? variant,
  String? messageID,
  List<PromptAttachment> attachments = const [],
}) => {
  'messageID': ?messageID,
  'model': ?model?.toJson(),
  'agent': ?agent,
  'variant': ?variant,
  'parts': [
    {'type': 'text', 'text': text},
    ...attachments.map((attachment) => attachment.toJson()),
  ],
};

Map<String, dynamic> shellRequestBody(
  String command, {
  String agent = 'build',
  ModelRef? model,
  String? variant,
}) => {
  'agent': agent,
  'model': ?model?.toJson(),
  'variant': ?variant,
  'command': command,
};

Map<String, dynamic> commandRequestBody(
  String command,
  String args, {
  ModelRef? model,
  String? variant,
}) => {
  'command': command,
  'arguments': args,
  'model': ?model?.wireName,
  'variant': ?variant,
};

// ---------------- Providers / agents ----------------

class ProviderInfo {
  final String id;
  final String name;
  final List<String> modelIDs;
  final Map<String, Map<String, dynamic>> modelData;
  ProviderInfo({
    required this.id,
    required this.name,
    required this.modelIDs,
    this.modelData = const {},
  });
}

class ProvidersResponse {
  final List<ProviderInfo> providers;
  final String? defaultProviderID;
  final String? defaultModelID;
  ProvidersResponse({
    required this.providers,
    this.defaultProviderID,
    this.defaultModelID,
  });

  factory ProvidersResponse.fromJson(Map<String, dynamic> j) {
    final providers = <ProviderInfo>[];
    final rawList = (j['providers'] as List?) ?? const [];
    for (final p in rawList) {
      if (p is! Map<String, dynamic>) continue;
      final id = p['id'].toString();
      final name = (p['name'] ?? id).toString();
      final models = <String>[];
      final modelData = <String, Map<String, dynamic>>{};
      final rawModels = p['models'];
      if (rawModels is Map<String, dynamic>) {
        for (final entry in rawModels.entries) {
          // Skip hidden/internal variants that opencode marks in metadata
          final m = entry.value;
          var hidden = false;
          if (m is Map<String, dynamic>) {
            modelData[entry.key] = Map<String, dynamic>.from(m);
            final opts = m['options'];
            if (opts is Map<String, dynamic>) hidden = opts['hidden'] == true;
          }
          if (!hidden) models.add(entry.key);
        }
      }
      providers.add(
        ProviderInfo(
          id: id,
          name: name,
          modelIDs: models,
          modelData: modelData,
        ),
      );
    }
    String? defP;
    String? defM;
    final d = j['default'];
    if (d is Map<String, dynamic> && d.isNotEmpty) {
      final first = d.entries.first;
      defP = first.key;
      defM = first.value.toString();
    } else if (d is String) {
      // some versions send "provider/model"
      final parts = d.split('/');
      if (parts.length == 2) {
        defP = parts[0];
        defM = parts[1];
      }
    }
    return ProvidersResponse(
      providers: providers,
      defaultProviderID: defP,
      defaultModelID: defM,
    );
  }
}

class AgentInfo {
  final String name;
  final String? mode;
  AgentInfo({required this.name, this.mode});

  factory AgentInfo.fromJson(Map<String, dynamic> j) => AgentInfo(
    name: (j['name'] ?? '').toString(),
    mode: j['mode']?.toString(),
  );
}

// ---------------- Files ----------------

class FileNode {
  final String name;
  final String path;
  final bool isDir;
  FileNode({required this.name, required this.path, required this.isDir});

  factory FileNode.fromJson(Map<String, dynamic> j) => FileNode(
    name: (j['name'] ?? '').toString(),
    path: (j['path'] ?? j['name'] ?? '').toString(),
    isDir: (j['type'] ?? 'file').toString() == 'directory',
  );
}

class FileContent {
  final String content;
  final String type;
  final String? encoding;
  final String? mimeType;

  const FileContent(
    this.content, {
    this.type = 'text',
    this.encoding,
    this.mimeType,
  });

  bool get isBinary => type == 'binary';

  factory FileContent.fromJson(Map<String, dynamic> j) {
    final c = j['content'];
    final type = (j['type'] ?? 'text').toString();
    final encoding = j['encoding']?.toString();
    final mimeType = j['mimeType']?.toString();
    if (c is String) {
      return FileContent(c, type: type, encoding: encoding, mimeType: mimeType);
    }
    if (c is List) {
      // Some versions return array of lines
      return FileContent(
        c.map((e) => e.toString()).join('\n'),
        type: type,
        encoding: encoding,
        mimeType: mimeType,
      );
    }
    return FileContent('', type: type, encoding: encoding, mimeType: mimeType);
  }

  Uint8List bytes() {
    if (encoding == 'base64') {
      try {
        return base64Decode(content);
      } on FormatException {
        return Uint8List(0);
      }
    }
    return Uint8List.fromList(utf8.encode(content));
  }
}

class FindMatch {
  final String path;
  final int lineNumber;
  final String snippet;
  FindMatch({
    required this.path,
    required this.lineNumber,
    required this.snippet,
  });

  factory FindMatch.fromJson(Map<String, dynamic> j) {
    final lines = j['lines'];
    String snip;
    if (lines is Map<String, dynamic>) {
      snip = lines.values.map((e) => e.toString()).join('\n').trimRight();
    } else if (lines is String) {
      snip = lines.trimRight();
    } else {
      snip = '';
    }
    return FindMatch(
      path: (j['path'] ?? '').toString(),
      lineNumber: _asInt(j['line_number']) ?? 0,
      snippet: snip,
    );
  }
}

class Todo {
  final String content;
  final String status;
  Todo({required this.content, required this.status});

  factory Todo.fromJson(Map<String, dynamic> j) => Todo(
    content: (j['content'] ?? '').toString(),
    status: (j['status'] ?? 'pending').toString(),
  );

  bool get done => status == 'completed';
}

class FileDiff {
  final String file;
  final String? before;
  final String? after;
  final String? patch;
  final int? additions;
  final int? deletions;
  final String? status;

  FileDiff({
    required this.file,
    this.before,
    this.after,
    this.patch,
    this.additions,
    this.deletions,
    this.status,
  });

  factory FileDiff.fromJson(Map<String, dynamic> j) => FileDiff(
    file: (j['file'] ?? '').toString(),
    before: j['before']?.toString(),
    after: j['after']?.toString(),
    patch: j['patch']?.toString(),
    additions: _asInt(j['additions']),
    deletions: _asInt(j['deletions']),
    status: j['status']?.toString(),
  );

  ({int added, int removed}) get counts =>
      additions != null || deletions != null
      ? (added: additions ?? 0, removed: deletions ?? 0)
      : countLineChanges(before, after);
}

// ---------------- Events / permissions ----------------

class EventEnvelope {
  final String type;
  final Map<String, dynamic> properties;
  EventEnvelope({required this.type, this.properties = const {}});

  factory EventEnvelope.fromJson(Map<String, dynamic> j) => EventEnvelope(
    type: (j['type'] ?? '').toString(),
    properties: j['properties'] is Map<String, dynamic>
        ? j['properties'] as Map<String, dynamic>
        : const {},
  );
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

  PermissionRequest({
    required this.id,
    required this.sessionID,
    required this.permission,
    this.patterns = const [],
    this.metadata = const {},
    this.always = const [],
    this.tool,
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
      );
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
