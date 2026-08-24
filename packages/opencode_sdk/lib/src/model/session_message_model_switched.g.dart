// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_model_switched.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageModelSwitched _$SessionMessageModelSwitchedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageModelSwitched', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'time', 'type', 'model']);
  final val = SessionMessageModelSwitched(
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
        _$SessionMessageModelSwitchedTypeEnumEnumMap,
        v,
        unknownValue: SessionMessageModelSwitchedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    model: $checkedConvert(
      'model',
      (v) => ModelRef.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageModelSwitchedToJson(
  SessionMessageModelSwitched instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'time': instance.time.toJson(),
  'type': _$SessionMessageModelSwitchedTypeEnumEnumMap[instance.type]!,
  'model': instance.model.toJson(),
};

const _$SessionMessageModelSwitchedTypeEnumEnumMap = {
  SessionMessageModelSwitchedTypeEnum.modelSwitched: 'model-switched',
  SessionMessageModelSwitchedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
