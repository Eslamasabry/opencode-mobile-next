part of '../models.dart';

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

/// Typed classification of an assistant message error, derived from the
/// server's error `name` (v1 `{name: 'ContextOverflowError', data}`) or
/// `type` (v2 structured errors). [unknown] covers every unrecognised name.
enum MessageErrorKind {
  contextOverflow,
  outputLength,
  providerAuth,
  contentFilter,
  aborted,
  modelNotFound,
  unknown;

  /// Maps a v1 error `name` or v2 error `type` string to a kind; null when
  /// [name] is null or empty (no error).
  static MessageErrorKind? fromName(String? name) {
    final trimmed = name?.trim();
    if (trimmed == null || trimmed.isEmpty) return null;
    final key = trimmed.toLowerCase().replaceAll(RegExp(r'[^a-z]'), '');
    if (key.contains('contextoverflow') || key.contains('contextlength')) {
      return contextOverflow;
    }
    if (key.contains('outputlength') || key == 'length') return outputLength;
    if (key.contains('providerauth') ||
        key.contains('needsauth') ||
        key.contains('unauthorized') ||
        key.contains('authentication')) {
      return providerAuth;
    }
    if (key.contains('contentfilter')) return contentFilter;
    if (key.contains('abort')) return aborted;
    if (key.contains('modelnotfound')) return modelNotFound;
    return unknown;
  }

  /// Some servers put the error class in the message text rather than the
  /// name ("ProviderModelNotFoundError: Model not found: …"). Recover the
  /// kind from the text when the name gave nothing better.
  static MessageErrorKind refineFromText(MessageErrorKind kind, String? text) {
    if (kind != unknown || text == null) return kind;
    // A sign-in the agent can no longer use, in its own words ("Not logged
    // in · Please run /login", "OAuth session expired"): the person signs in
    // again. Not the transient "another process is refreshing it".
    if (_signInGone.hasMatch(text)) return providerAuth;
    final head = text.trimLeft().split(RegExp(r'[:\s]')).first;
    return fromName(head) ?? unknown;
  }

  static final _signInGone = RegExp(
    r'not logged in|please run /login|failed to authenticate|'
    r'oauth (session|token) (has )?expired|could not be refreshed|'
    r'invalid api key|authentication_error',
    caseSensitive: false,
  );
}

/// Readable text for a session failure whose server message is empty,
/// chosen by the error's [MessageErrorKind]. OpenCode 2 beta-18600 sends
/// `{type: 'provider.auth', message: ''}` when a provider rejects the
/// model's credentials; an empty banner tells the user nothing.
String sessionErrorFallbackText(String? name) {
  final kind = MessageErrorKind.fromName(name) ?? MessageErrorKind.unknown;
  return switch (kind) {
    MessageErrorKind.providerAuth =>
      'The provider rejected this model\'s credentials. Sign in to the '
          'provider again under Library › Providers, then retry.',
    MessageErrorKind.contextOverflow =>
      'The conversation no longer fits the model\'s context window. '
          'Compact the session or start a new one.',
    MessageErrorKind.outputLength =>
      'The model stopped because its reply reached the output limit.',
    MessageErrorKind.contentFilter =>
      'The provider\'s content filter blocked this reply.',
    MessageErrorKind.aborted => 'The run was stopped before it finished.',
    MessageErrorKind.modelNotFound =>
      'The selected model is not available on this server. Choose another '
          'model.',
    MessageErrorKind.unknown =>
      name == null || name.trim().isEmpty
          ? 'The session run failed.'
          : 'The session run failed ($name).',
  };
}

/// The first meaningful line of an error, without the stack trace a server
/// may append. What the user reads; the full text stays behind Details.
String errorHeadline(String text) {
  final lines = text.trim().split('\n');
  final first = lines
      .firstWhere(
        (line) => line.trim().isNotEmpty && !line.trimLeft().startsWith('at '),
        orElse: () => lines.first,
      )
      .trim();
  // "SomethingError: message" reads better as just the message.
  final colon = first.indexOf(': ');
  if (colon > 0 &&
      RegExp(r'^[A-Za-z]+Error$').hasMatch(first.substring(0, colon))) {
    return first.substring(colon + 2);
  }
  return first;
}

/// True when [text] carries more than its headline (a stack trace or a
/// second paragraph) and deserves a Details affordance.
bool errorHasDetails(String text) => text.trim().contains('\n');

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

  /// Provider finish reason as the server reports it (v1/v2 `finish`, e.g.
  /// `stop`, `length`, `tool-calls`, `content-filter`, `error`).
  final String? finish;

  /// Typed classification of [errorText]; null when the message has no error.
  final MessageErrorKind? errorKind;

  /// For an assistant message, the id of the user message it answers (v1
  /// `parentID`). Null on servers that do not report it; callers that need
  /// exact turn correlation must treat null as "unknown", not "none".
  final String? parentID;

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
    this.finish,
    MessageErrorKind? errorKind,
    this.parentID,
  }) : tokens = tokens ?? Tokens(),
       errorKind =
           errorKind ?? (errorText == null ? null : MessageErrorKind.unknown);

  factory MessageInfo.fromJson(Map<String, dynamic> j) {
    final err = j['error'];
    String? errText;
    MessageErrorKind? errKind;
    if (err is Map<String, dynamic>) {
      final data = err['data'];
      final nestedMessage = data is Map ? data['message'] : null;
      errText = (err['message'] ?? nestedMessage ?? err['name'])?.toString();
      errKind =
          MessageErrorKind.fromName((err['name'] ?? err['type'])?.toString()) ??
          (errText == null ? null : MessageErrorKind.unknown);
    } else if (err is String) {
      errText = err;
      errKind = MessageErrorKind.fromName(err) ?? MessageErrorKind.unknown;
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
      finish: j['finish']?.toString(),
      errorKind: errKind,
      parentID: j['parentID'] is String && (j['parentID'] as String).isNotEmpty
          ? j['parentID'] as String
          : null,
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
  final String status; // pending | running | completed | error | cancelled
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

  /// When the tool started running (v1 `time.start`, v2 `time.ran` falling
  /// back to `time.created`); null while pending or when the server omitted it.
  final DateTime? startedAt;

  /// When the tool finished (v1 `time.end`, v2 `time.completed`).
  final DateTime? completedAt;

  /// True when the server pruned/compacted this tool's output out of the
  /// context (v1 `time.compacted`, v2 `time.pruned`).
  final bool pruned;

  /// False when the model requested the call but the server never ran it
  /// (v2 `executed: false`); v1 payloads are always executed.
  final bool executed;

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
    this.startedAt,
    this.completedAt,
    this.pruned = false,
    this.executed = true,
  });

  /// Wall-clock run time; null until both timestamps are known.
  Duration? get duration {
    final start = startedAt;
    final end = completedAt;
    if (start == null || end == null) return null;
    final elapsed = end.difference(start);
    return elapsed.isNegative ? Duration.zero : elapsed;
  }

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
      // `cancelled`: stopped before it finished (a Paseo step a mid-turn
      // message interrupted); the tool card reads it as Stopped.
      'running' ||
      'pending' ||
      'completed' ||
      'error' ||
      'cancelled' => rawStatus,
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
    final time = v['time'] is Map ? v['time'] as Map : const {};
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
      startedAt: _asDateTime(time['start']),
      completedAt: _asDateTime(time['end']),
      pruned: _asInt(time['compacted']) != null || v['pruned'] == true,
      executed: _toolRan(v, time),
    );
  }

  /// Whether the tool actually ran. beta-18600 stamps `executed: false` on
  /// tools it plainly ran (status completed, `time.ran` set, output present)
  /// — the flag marks provider-side execution, not "never ran" — so the
  /// outcome wins over the flag: a finished or failed call, or one with a
  /// run timestamp, counts as executed. Only a call that never progressed
  /// past pending/running with `executed: false` reads as not run.
  static bool _toolRan(Map<String, dynamic> v, Map time) {
    if (v['executed'] != false) return true;
    final status = v['status']?.toString();
    if (status == 'completed' || status == 'error') return true;
    return _asDateTime(time['start']) != null ||
        _asDateTime(time['end']) != null;
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

  /// A small, allowlisted presentation contract for server-authored notices.
  /// It contains no arbitrary provider/configuration metadata.
  final Map<String, String> noticeMetadata;

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
    this.noticeMetadata = const {},
  }) : toolState = toolState ?? ToolState(status: 'pending');

  bool get isRenderable =>
      !synthetic &&
      ((type == 'text' && text.trim().isNotEmpty) ||
          (type == 'reasoning' && text.trim().isNotEmpty) ||
          type == 'tool' ||
          type == 'file');

  factory Part.fromJson(Map<String, dynamic> j) {
    // A command that runs as a sub-agent arrives as a user message holding
    // only a `subtask` part. Its description is what the person asked for;
    // without it the conversation would show an empty turn.
    final subtask = j['type'] == 'subtask';
    final request = subtask
        ? (j['description'] as String?)?.trim().isNotEmpty == true
              ? j['description'] as String
              : (j['prompt'] as String?) ?? ''
        : null;
    return Part(
      id: (j['id'] ?? j['callID'])?.toString(),
      type: subtask ? 'text' : (j['type'] as String?) ?? 'text',
      text: request ?? (j['text'] as String?) ?? '',
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
