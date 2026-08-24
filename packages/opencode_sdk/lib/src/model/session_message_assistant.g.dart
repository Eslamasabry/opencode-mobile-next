// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_assistant.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageAssistant _$SessionMessageAssistantFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageAssistant', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['id', 'time', 'type', 'agent', 'model', 'content'],
  );
  final val = SessionMessageAssistant(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    time: $checkedConvert(
      'time',
      (v) => SessionMessageShellTime.fromJson(v as Map<String, dynamic>),
    ),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionMessageAssistantTypeEnumEnumMap,
        v,
        unknownValue: SessionMessageAssistantTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    agent: $checkedConvert('agent', (v) => v as String),
    model: $checkedConvert(
      'model',
      (v) => ModelRef.fromJson(v as Map<String, dynamic>),
    ),
    content: $checkedConvert(
      'content',
      (v) => (v as List<dynamic>).map(OpencodeSdkRawUnion021.fromJson).toList(),
    ),
    snapshot: $checkedConvert(
      'snapshot',
      (v) => v == null
          ? null
          : SessionMessageAssistantSnapshot.fromJson(v as Map<String, dynamic>),
    ),
    finish: $checkedConvert('finish', (v) => v as String?),
    cost: $checkedConvert('cost', (v) => v as num?),
    tokens: $checkedConvert(
      'tokens',
      (v) =>
          v == null ? null : SessionTokens.fromJson(v as Map<String, dynamic>),
    ),
    error: $checkedConvert(
      'error',
      (v) => v == null
          ? null
          : SessionErrorUnknown.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageAssistantToJson(
  SessionMessageAssistant instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'time': instance.time.toJson(),
  'type': _$SessionMessageAssistantTypeEnumEnumMap[instance.type]!,
  'agent': instance.agent,
  'model': instance.model.toJson(),
  'content': instance.content.map((e) => e.toJson()).toList(),
  'snapshot': ?instance.snapshot?.toJson(),
  'finish': ?instance.finish,
  'cost': ?instance.cost,
  'tokens': ?instance.tokens?.toJson(),
  'error': ?instance.error?.toJson(),
};

const _$SessionMessageAssistantTypeEnumEnumMap = {
  SessionMessageAssistantTypeEnum.assistant: 'assistant',
  SessionMessageAssistantTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
