// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_system.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageSystem _$SessionMessageSystemFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageSystem', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'time', 'type', 'text']);
  final val = SessionMessageSystem(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    time: $checkedConvert(
      'time',
      (v) =>
          SessionMessageAgentSwitchedTime.fromJson(v as Map<String, dynamic>),
    ),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionMessageSystemTypeEnumEnumMap,
        v,
        unknownValue: SessionMessageSystemTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    text: $checkedConvert('text', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageSystemToJson(
  SessionMessageSystem instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'time': instance.time.toJson(),
  'type': _$SessionMessageSystemTypeEnumEnumMap[instance.type]!,
  'text': instance.text,
};

const _$SessionMessageSystemTypeEnumEnumMap = {
  SessionMessageSystemTypeEnum.system: 'system',
  SessionMessageSystemTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
