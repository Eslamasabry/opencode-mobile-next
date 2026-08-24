// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_model_switched.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextModelSwitched _$SessionNextModelSwitchedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextModelSwitched', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextModelSwitched(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextModelSwitchedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextModelSwitchedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    durable: $checkedConvert(
      'durable',
      (v) => v == null
          ? null
          : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
    ),
    location: $checkedConvert(
      'location',
      (v) => v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextModelSwitchedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextModelSwitchedToJson(
  SessionNextModelSwitched instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextModelSwitchedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextModelSwitchedTypeEnumEnumMap = {
  SessionNextModelSwitchedTypeEnum.sessionPeriodNextPeriodModelPeriodSwitched:
      'session.next.model.switched',
  SessionNextModelSwitchedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
