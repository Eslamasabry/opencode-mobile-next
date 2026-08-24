// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_assistant_reasoning.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageAssistantReasoning _$SessionMessageAssistantReasoningFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageAssistantReasoning', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'text']);
  final val = SessionMessageAssistantReasoning(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionMessageAssistantReasoningTypeEnumEnumMap,
        v,
        unknownValue:
            SessionMessageAssistantReasoningTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    text: $checkedConvert('text', (v) => v as String),
    providerMetadata: $checkedConvert(
      'providerMetadata',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as Object)),
    ),
    time: $checkedConvert(
      'time',
      (v) => v == null
          ? null
          : SessionMessageShellTime.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageAssistantReasoningToJson(
  SessionMessageAssistantReasoning instance,
) => <String, dynamic>{
  'type': _$SessionMessageAssistantReasoningTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'text': instance.text,
  'providerMetadata': ?instance.providerMetadata,
  'time': ?instance.time?.toJson(),
};

const _$SessionMessageAssistantReasoningTypeEnumEnumMap = {
  SessionMessageAssistantReasoningTypeEnum.reasoning: 'reasoning',
  SessionMessageAssistantReasoningTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
