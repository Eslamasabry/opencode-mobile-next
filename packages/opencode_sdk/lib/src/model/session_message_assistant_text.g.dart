// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_assistant_text.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageAssistantText _$SessionMessageAssistantTextFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageAssistantText', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'text']);
  final val = SessionMessageAssistantText(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionMessageAssistantTextTypeEnumEnumMap,
        v,
        unknownValue: SessionMessageAssistantTextTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    text: $checkedConvert('text', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageAssistantTextToJson(
  SessionMessageAssistantText instance,
) => <String, dynamic>{
  'type': _$SessionMessageAssistantTextTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'text': instance.text,
};

const _$SessionMessageAssistantTextTypeEnumEnumMap = {
  SessionMessageAssistantTextTypeEnum.text: 'text',
  SessionMessageAssistantTextTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
