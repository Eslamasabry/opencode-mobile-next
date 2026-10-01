part of '../models.dart';

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
