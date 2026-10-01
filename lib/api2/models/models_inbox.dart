part of '../models.dart';

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
