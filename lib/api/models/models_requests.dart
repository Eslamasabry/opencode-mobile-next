part of '../models.dart';

// ---------------- Requests ----------------

class ModelRef {
  final String providerID;
  final String modelID;
  ModelRef({required this.providerID, required this.modelID});

  /// A ref whose model id is guaranteed bare. Some catalog shapes carry the
  /// model under a composite key ("openai/gpt-5") while the prompt endpoints
  /// want the provider and the bare id separately; sending the composite as
  /// the id produces "Model not found: openai/gpt-5. Did you mean: gpt-5?".
  ModelRef get normalized {
    final bare = bareModelID(providerID, modelID);
    return bare == modelID
        ? this
        : ModelRef(providerID: providerID, modelID: bare);
  }

  Map<String, dynamic> toJson() => {
    'providerID': providerID,
    'modelID': modelID,
  };

  String get wireName => '$providerID/$modelID';
}

/// Strips a leading `providerID/` from a model id that arrived as a
/// composite key. Leaves ids that merely contain a slash (dated snapshots
/// like `provider-x/model`) alone unless the prefix is this provider.
String bareModelID(String providerID, String modelID) {
  final prefix = '$providerID/';
  if (providerID.isNotEmpty && modelID.startsWith(prefix)) {
    final rest = modelID.substring(prefix.length);
    if (rest.isNotEmpty) return rest;
  }
  return modelID;
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
    final hasConnectedList = j['connected'] is List;
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
      hasConnectedList
          ? connected.map((id) => availableByID[id]).whereType<ProviderInfo>()
          : availableProviders,
    );
    String? defP;
    String? defM;
    final d = j['default'];
    if (d is Map<String, dynamic> && d.isNotEmpty) {
      final defaults = !hasConnectedList
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

  /// Raw agent colour as the server gives it (`#rrggbb` or a theme token
  /// such as `primary`); null when unset.
  final String? color;

  /// Agent's configured model as `providerID/modelID`; null when it inherits.
  final String? model;
  AgentInfo({required this.name, this.mode, this.color, this.model});

  factory AgentInfo.fromJson(Map<String, dynamic> j) => AgentInfo(
    name: (j['name'] ?? '').toString(),
    mode: j['mode']?.toString(),
    color: j['color']?.toString(),
    model: modelRefString(j['model']),
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
