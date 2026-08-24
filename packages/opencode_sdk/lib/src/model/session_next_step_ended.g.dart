// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_step_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextStepEnded _$SessionNextStepEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextStepEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextStepEnded(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextStepEndedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextStepEndedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextStepEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextStepEndedToJson(
  SessionNextStepEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextStepEndedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextStepEndedTypeEnumEnumMap = {
  SessionNextStepEndedTypeEnum.sessionPeriodNextPeriodStepPeriodEnded:
      'session.next.step.ended',
  SessionNextStepEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
