// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_synthetic.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageSynthetic _$SessionMessageSyntheticFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageSynthetic', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['id', 'time', 'sessionID', 'text', 'type'],
  );
  final val = SessionMessageSynthetic(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    time: $checkedConvert(
      'time',
      (v) =>
          SessionMessageAgentSwitchedTime.fromJson(v as Map<String, dynamic>),
    ),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    text: $checkedConvert('text', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionMessageSyntheticTypeEnumEnumMap,
        v,
        unknownValue: SessionMessageSyntheticTypeEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageSyntheticToJson(
  SessionMessageSynthetic instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'time': instance.time.toJson(),
  'sessionID': instance.sessionID,
  'text': instance.text,
  'type': _$SessionMessageSyntheticTypeEnumEnumMap[instance.type]!,
};

const _$SessionMessageSyntheticTypeEnumEnumMap = {
  SessionMessageSyntheticTypeEnum.synthetic: 'synthetic',
  SessionMessageSyntheticTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
