// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_assistant_tool.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageAssistantTool _$SessionMessageAssistantToolFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageAssistantTool', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'name', 'state', 'time']);
  final val = SessionMessageAssistantTool(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionMessageAssistantToolTypeEnumEnumMap,
        v,
        unknownValue: SessionMessageAssistantToolTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    name: $checkedConvert('name', (v) => v as String),
    provider: $checkedConvert(
      'provider',
      (v) => v == null
          ? null
          : SessionMessageAssistantToolProvider.fromJson(
              v as Map<String, dynamic>,
            ),
    ),
    state: $checkedConvert('state', (v) => OpencodeSdkRawUnion020.fromJson(v)),
    time: $checkedConvert(
      'time',
      (v) =>
          SessionMessageAssistantToolTime.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageAssistantToolToJson(
  SessionMessageAssistantTool instance,
) => <String, dynamic>{
  'type': _$SessionMessageAssistantToolTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'name': instance.name,
  'provider': ?instance.provider?.toJson(),
  'state': instance.state.toJson(),
  'time': instance.time.toJson(),
};

const _$SessionMessageAssistantToolTypeEnumEnumMap = {
  SessionMessageAssistantToolTypeEnum.tool: 'tool',
  SessionMessageAssistantToolTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
