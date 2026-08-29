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
  final String? projectID;
  final String? workspaceID;
  final String? parentID;
  final String? directory;
  final String? path;
  final bool reverted;
  final String? shareUrl;
  final SessionTime? time;
  Session({
    required this.id,
    this.title,
    this.projectID,
    this.workspaceID,
    this.parentID,
    this.directory,
    this.path,
    this.reverted = false,
    this.shareUrl,
    this.time,
  });

  factory Session.fromJson(Map<String, dynamic> j) => Session(
    id: j['id'] as String,
    title: j['title'] as String?,
    projectID: j['projectID'] as String?,
    workspaceID: j['workspaceID'] as String?,
    parentID: j['parentID'] as String?,
    directory: j['directory'] as String?,
    path: j['path'] as String?,
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
  final int cacheRead;
  final int cacheWrite;
  Tokens({
    this.input = 0,
    this.output = 0,
    this.reasoning = 0,
    this.cacheRead = 0,
    this.cacheWrite = 0,
  });

  factory Tokens.fromJson(dynamic v) {
    if (v is! Map<String, dynamic>) return Tokens();
    final cache = v['cache'];
    return Tokens(
      input: _asInt(v['input']) ?? 0,
      output: _asInt(v['output']) ?? 0,
      reasoning: _asInt(v['reasoning']) ?? 0,
      cacheRead: cache is Map ? _asInt(cache['read']) ?? 0 : 0,
      cacheWrite: cache is Map ? _asInt(cache['write']) ?? 0 : 0,
    );
  }

  int get cache => cacheRead + cacheWrite;

  /// The same context-total definition used by the upstream OpenCode client.
  int get total => input + output + reasoning + cache;
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

class ToolOutputFile {
  final String? path;
  final String? url;
  final String? mimeType;
  final String? filename;

  const ToolOutputFile({this.path, this.url, this.mimeType, this.filename});

  String get displayName {
    final explicit = filename?.trim();
    if (explicit?.isNotEmpty == true) return explicit!;
    final location = path?.trim().isNotEmpty == true ? path! : url ?? '';
    final withoutQuery = location.split('?').first;
    final segments = withoutQuery.replaceAll('\\', '/').split('/');
    return segments.lastWhere(
      (segment) => segment.isNotEmpty,
      orElse: () => 'Generated file',
    );
  }

  bool get isImage {
    if (mimeType?.toLowerCase().startsWith('image/') == true) return true;
    final name = displayName.toLowerCase();
    return const [
      '.png',
      '.jpg',
      '.jpeg',
      '.gif',
      '.webp',
      '.bmp',
    ].any(name.endsWith);
  }

  String get identity => [
    path ?? '',
    filename ?? '',
    mimeType ?? '',
    if (url != null)
      '${url!.length}:${url!.substring(0, url!.length.clamp(0, 80))}',
  ].join('|');
}

List<ToolOutputFile> _toolOutputFiles({
  required dynamic output,
  required dynamic attachments,
  required Map<String, dynamic> input,
  required Map<String, dynamic>? metadata,
  required String? toolName,
}) {
  final files = <ToolOutputFile>[];
  final locations = <String>{};

  void add({String? path, String? url, String? mimeType, String? filename}) {
    final normalizedPath = path?.trim();
    final normalizedUrl = url?.trim();
    final location = normalizedPath?.isNotEmpty == true
        ? normalizedPath!
        : normalizedUrl?.isNotEmpty == true
        ? normalizedUrl!
        : null;
    if (location == null || !locations.add(location)) return;
    final file = ToolOutputFile(
      path: normalizedPath?.isNotEmpty == true ? normalizedPath : null,
      url: normalizedUrl?.isNotEmpty == true ? normalizedUrl : null,
      mimeType: mimeType?.trim(),
      filename: filename?.trim(),
    );
    files.add(file);
  }

  void scanExplicitFiles(
    dynamic value, {
    bool allowBareUrl = false,
    int depth = 0,
  }) {
    if (value == null || depth > 6 || files.length >= 8) return;
    if (value is List) {
      for (final item in value) {
        scanExplicitFiles(item, allowBareUrl: allowBareUrl, depth: depth + 1);
      }
      return;
    }
    if (value is Map) {
      final map = Map<String, dynamic>.from(value);
      final hasExplicitFilePath =
          map.containsKey('filePath') ||
          map.containsKey('filepath') ||
          map.containsKey('file_path');
      final rawPath = hasExplicitFilePath
          ? map['filePath'] ?? map['filepath'] ?? map['file_path']
          : map['path'];
      final rawUrl = map['url'];
      final mime = (map['mime'] ?? map['mimeType'])?.toString();
      final filename = (map['filename'] ?? map['name'])?.toString();
      final path = rawPath?.toString();
      final url = rawUrl?.toString();
      final hasFileSignal =
          hasExplicitFilePath ||
          mime?.trim().isNotEmpty == true ||
          filename?.trim().isNotEmpty == true ||
          (allowBareUrl && url?.trim().isNotEmpty == true);
      if ((path?.trim().isNotEmpty == true && hasExplicitFilePath) ||
          (url?.trim().isNotEmpty == true && hasFileSignal) ||
          mime?.trim().isNotEmpty == true) {
        if (url?.startsWith('file://') == true && path == null) {
          add(
            path: Uri.tryParse(url!)?.toFilePath(),
            mimeType: mime,
            filename: filename,
          );
        } else {
          add(path: path, url: url, mimeType: mime, filename: filename);
        }
      }
      for (final item in map.values) {
        scanExplicitFiles(item, allowBareUrl: allowBareUrl, depth: depth + 1);
      }
    }
  }

  dynamic decodedOutput = output;
  if (output is String) {
    final trimmed = output.trim();
    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) ||
        (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
      try {
        decodedOutput = jsonDecode(trimmed);
      } catch (_) {}
    }
  }
  scanExplicitFiles(attachments, allowBareUrl: true);
  scanExplicitFiles(decodedOutput);

  final normalizedTool = toolName?.toLowerCase();
  if (normalizedTool == 'read' && files.isNotEmpty) {
    final sourcePath = input['filePath']?.toString();
    if (sourcePath?.trim().isNotEmpty == true) {
      final sourceName = sourcePath!.replaceAll('\\', '/').split('/').last;
      for (var index = 0; index < files.length; index += 1) {
        final file = files[index];
        files[index] = ToolOutputFile(
          path: file.path,
          url: file.url,
          mimeType: file.mimeType,
          filename: file.filename ?? sourceName,
        );
      }
    }
  }
  final outputPath = metadata?['outputPath']?.toString();
  if ((normalizedTool == 'bash' || normalizedTool == 'shell') &&
      outputPath?.trim().isNotEmpty == true) {
    add(path: outputPath, filename: outputPath!.split('/').last);
  }
  return List.unmodifiable(files);
}

/// One ordered item of a v2 tool result's `content` array: either a text run
/// or a file attachment. v1 payloads carry a single output string and never
/// populate these, so [ToolState.segments] stays empty on v1 servers.
class ToolResultSegment {
  final String? text;
  final ToolOutputFile? file;

  const ToolResultSegment.text(String this.text) : file = null;
  const ToolResultSegment.file(ToolOutputFile this.file) : text = null;

  bool get isFile => file != null;
}

/// Tool state extracted from a tool part's `state` object.
class ToolState {
  final String status; // pending | running | completed | error
  final String? title;
  final Map<String, dynamic> input;
  final String? inputJson;
  final String? output;
  final dynamic outputValue;
  final Map<String, dynamic>? metadata;
  final List<ToolOutputFile> outputFiles;

  /// Ordered text/file interleaving of the tool result (v2 `content` arrays).
  /// Empty when the server gave a single output string (all v1 payloads).
  final List<ToolResultSegment> segments;

  ToolState({
    required this.status,
    this.title,
    this.input = const {},
    this.inputJson,
    this.output,
    this.outputValue,
    this.metadata,
    this.outputFiles = const [],
    this.segments = const [],
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

  factory ToolState.fromJson(dynamic v, {String? toolName}) {
    if (v is! Map<String, dynamic>) {
      return ToolState(status: 'pending');
    }
    final rawStatus = (v['status'] ?? v['type'] ?? 'pending').toString();
    final status = switch (rawStatus) {
      'running' || 'pending' || 'completed' || 'error' => rawStatus,
      _ => 'running',
    };
    final rawInput = v['input'];
    final structuredInput = rawInput is Map
        ? Map<String, dynamic>.from(rawInput)
        : const <String, dynamic>{};
    final meta = v['metadata'] is Map
        ? Map<String, dynamic>.from(v['metadata'] as Map)
        : null;
    final inputText = status == 'pending'
        ? _pretty(v['raw'])
        : _pretty(rawInput);
    final structuredOutput = switch (status) {
      'completed' => v['output'],
      'error' => v['error'],
      _ => null,
    };
    final outputText = switch (status) {
      'completed' || 'error' => _pretty(structuredOutput),
      _ => '',
    };
    return ToolState(
      status: status,
      title: v['title']?.toString(),
      input: structuredInput,
      inputJson: inputText,
      output: outputText,
      outputValue: structuredOutput,
      metadata: meta,
      outputFiles: _toolOutputFiles(
        output: v['output'],
        attachments: v['attachments'],
        input: structuredInput,
        metadata: meta,
        toolName: toolName,
      ),
      segments: _segmentsFromJson(v['contentSegments']),
    );
  }

  /// Parses the ordered `contentSegments` list the v2 mapper attaches; v1
  /// payloads never carry the key.
  static List<ToolResultSegment> _segmentsFromJson(dynamic raw) {
    if (raw is! List) return const [];
    final segments = <ToolResultSegment>[];
    for (final item in raw) {
      if (item is! Map) continue;
      final type = item['type']?.toString();
      if (type == 'text') {
        final text = item['text']?.toString() ?? '';
        if (text.isNotEmpty) segments.add(ToolResultSegment.text(text));
      } else if (type == 'file') {
        final url = item['url']?.toString();
        if (url == null || url.isEmpty) continue;
        segments.add(
          ToolResultSegment.file(
            ToolOutputFile(
              url: url,
              mimeType: item['mime']?.toString(),
              filename: item['name']?.toString(),
            ),
          ),
        );
      }
    }
    return List.unmodifiable(segments);
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
      toolState: ToolState.fromJson(
        j['state'],
        toolName: j['tool']?.toString(),
      ),
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
  static const directoryReferenceMime = 'application/x-directory';

  final String mime;
  final String filename;
  final String url;

  const PromptAttachment({
    required this.mime,
    required this.filename,
    required this.url,
  });

  factory PromptAttachment.reference({
    required String name,
    required String path,
  }) => PromptAttachment(
    mime: directoryReferenceMime,
    filename: name,
    url: _referenceUrl(path),
  );

  bool get isDirectoryReference =>
      mime == directoryReferenceMime && Uri.tryParse(url)?.scheme == 'file';

  static String _referenceUrl(String path) {
    final windows =
        RegExp(r'^[A-Za-z]:[\\/]').hasMatch(path) || path.startsWith(r'\\');
    return Uri.file(path, windows: windows).toString();
  }

  Map<String, dynamic> toJson() => {
    'type': 'file',
    'mime': mime,
    'filename': filename,
    'url': url,
  };
}

/// A server-authored subagent mention embedded in the prompt text.
///
/// [start] and [end] are UTF-16 code-unit offsets, matching both Dart strings
/// and OpenCode's JavaScript wire contract.
class PromptAgentMention {
  final String name;
  final String value;
  final int start;
  final int end;

  const PromptAgentMention({
    required this.name,
    required this.value,
    required this.start,
    required this.end,
  });
}

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
  final List<ProviderInfo> availableProviders;
  final String? defaultProviderID;
  final String? defaultModelID;
  ProvidersResponse({
    required this.providers,
    List<ProviderInfo>? availableProviders,
    this.defaultProviderID,
    this.defaultModelID,
  }) : availableProviders = availableProviders ?? providers;

  factory ProvidersResponse.fromJson(Map<String, dynamic> j) {
    final providers = <ProviderInfo>[];
    final connected = (j['connected'] as List? ?? const [])
        .map((value) => value.toString())
        .where((value) => value.isNotEmpty)
        .toList();
    final rawList =
        (j['all'] as List?) ?? (j['providers'] as List?) ?? const [];
    final rawByID = <String, Map<String, dynamic>>{};
    for (final p in rawList) {
      if (p is! Map<String, dynamic>) continue;
      rawByID[p['id'].toString()] = p;
    }
    ProviderInfo parseProvider(Map<String, dynamic> p) {
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
      return ProviderInfo(
        id: id,
        name: name,
        modelIDs: models,
        modelData: modelData,
      );
    }

    final availableProviders = rawByID.values.map(parseProvider).toList();
    final availableByID = {
      for (final provider in availableProviders) provider.id: provider,
    };
    providers.addAll(
      connected.isEmpty
          ? availableProviders
          : connected.map((id) => availableByID[id]).whereType<ProviderInfo>(),
    );
    String? defP;
    String? defM;
    final d = j['default'];
    if (d is Map<String, dynamic> && d.isNotEmpty) {
      final defaults = connected.isEmpty
          ? d.entries
          : connected
                .map((id) => MapEntry(id, d[id]))
                .where((e) => e.value != null);
      if (defaults.isNotEmpty) {
        final first = defaults.first;
        defP = first.key;
        defM = first.value.toString();
      }
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
      availableProviders: availableProviders,
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
  final String? priority;

  Todo({required this.content, required this.status, this.priority});

  factory Todo.fromJson(Map<String, dynamic> j) => Todo(
    content: (j['content'] ?? '').toString(),
    status: (j['status'] ?? 'pending').toString(),
    priority: j['priority']?.toString(),
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
