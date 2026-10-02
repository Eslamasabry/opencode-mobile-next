/// Minimal ACP v1 models. Unknown additions remain available in [fields].
/// Wire fields are untrusted and may contain private content: never log them.
library;

typedef AcpJson = Map<String, Object?>;

AcpJson acpObject(Object? value) {
  if (value is! Map<String, dynamic>) {
    throw const FormatException('Invalid ACP object');
  }
  return Map<String, Object?>.unmodifiable(value);
}

String acpString(AcpJson fields, String key) {
  final value = fields[key];
  if (value is! String || value.isEmpty) {
    throw const FormatException('Invalid ACP field');
  }
  return value;
}

class AcpInitializeResult {
  AcpInitializeResult.fromJson(AcpJson json)
    : fields = json,
      protocolVersion = json['protocolVersion'] as int,
      capabilities = AcpAgentCapabilities.fromJson(
        acpObject(json['agentCapabilities']),
      ),
      authMethods = List.unmodifiable(
        (json['authMethods'] as List? ?? const []).map(
          (value) => AcpAuthMethod.fromJson(acpObject(value)),
        ),
      );

  final AcpJson fields;
  final int protocolVersion;
  final AcpAgentCapabilities capabilities;
  final List<AcpAuthMethod> authMethods;
}

class AcpAgentCapabilities {
  AcpAgentCapabilities.fromJson(this.fields);
  final AcpJson fields;
  bool get loadSession => fields['loadSession'] == true;
  bool get supportsImages =>
      (fields['promptCapabilities'] as Map?)?['image'] == true;
}

class AcpAuthMethod {
  AcpAuthMethod.fromJson(AcpJson json)
    : fields = json,
      id = acpString(json, 'id'),
      name = acpString(json, 'name');
  final AcpJson fields;
  final String id;
  final String name;
}

class AcpSession {
  AcpSession.fromJson(AcpJson json)
    : fields = json,
      sessionId = acpString(json, 'sessionId');
  final AcpJson fields;
  final String sessionId;
}

class AcpPromptResult {
  AcpPromptResult.fromJson(AcpJson json)
    : fields = json,
      stopReason = acpString(json, 'stopReason');
  final AcpJson fields;
  // String intentionally permits future stop reasons.
  final String stopReason;
}

class AcpContent {
  AcpContent.fromJson(AcpJson json)
    : fields = json,
      type = acpString(json, 'type');
  final AcpJson fields;
  final String type;
  String? get text => fields['text'] as String?;
}

class AcpToolContent {
  AcpToolContent.fromJson(AcpJson json)
    : fields = json,
      type = acpString(json, 'type');
  final AcpJson fields;
  final String type;
  String? get path => fields['path'] as String?;
  String? get oldText => fields['oldText'] as String?;
  String? get newText => fields['newText'] as String?;
  String? get terminalId => fields['terminalId'] as String?;
  AcpContent? get content => fields['content'] == null
      ? null
      : AcpContent.fromJson(acpObject(fields['content']));
}

class AcpToolCall {
  AcpToolCall.fromJson(AcpJson json)
    : fields = json,
      toolCallId = acpString(json, 'toolCallId'),
      content = List.unmodifiable(
        (json['content'] as List? ?? const []).map(
          (value) => AcpToolContent.fromJson(acpObject(value)),
        ),
      );
  final AcpJson fields;
  final String toolCallId;
  final List<AcpToolContent> content;
  String? get title => fields['title'] as String?;
  String? get kind => fields['kind'] as String?;
  String? get status => fields['status'] as String?;
}

class AcpSessionUpdate {
  AcpSessionUpdate.fromJson(AcpJson json)
    : fields = json,
      sessionId = acpString(json, 'sessionId'),
      update = acpObject(json['update']) {
    kind = acpString(update, 'sessionUpdate');
    if (kind == 'tool_call' || kind == 'tool_call_update') {
      toolCall = AcpToolCall.fromJson(update);
    } else if (kind == 'agent_message_chunk' ||
        kind == 'agent_thought_chunk' ||
        kind == 'user_message_chunk') {
      content = AcpContent.fromJson(acpObject(update['content']));
    }
  }
  final AcpJson fields;
  final String sessionId;
  final AcpJson update;
  late final String kind;
  AcpContent? content;
  AcpToolCall? toolCall;
}

class AcpPermissionOption {
  AcpPermissionOption.fromJson(AcpJson json)
    : fields = json,
      optionId = acpString(json, 'optionId'),
      name = acpString(json, 'name'),
      kind = acpString(json, 'kind');
  final AcpJson fields;
  final String optionId;
  final String name;
  final String kind;
  bool get isKnownKind => const {
    'allow_once',
    'allow_always',
    'reject_once',
    'reject_always',
  }.contains(kind);
}

class AcpPermissionRequest {
  AcpPermissionRequest.fromJson(AcpJson json)
    : fields = json,
      sessionId = acpString(json, 'sessionId'),
      toolCall = AcpToolCall.fromJson(acpObject(json['toolCall'])),
      options = List.unmodifiable(
        (json['options'] as List).map(
          (value) => AcpPermissionOption.fromJson(acpObject(value)),
        ),
      );
  final AcpJson fields;
  final String sessionId;
  final AcpToolCall toolCall;
  final List<AcpPermissionOption> options;
}
