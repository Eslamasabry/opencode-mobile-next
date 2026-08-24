// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_step_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextStepFailed _$SessionNextStepFailedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextStepFailed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextStepFailed(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextStepFailedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextStepFailedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextStepFailedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextStepFailedToJson(
  SessionNextStepFailed instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextStepFailedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextStepFailedTypeEnumEnumMap = {
  SessionNextStepFailedTypeEnum.sessionPeriodNextPeriodStepPeriodFailed:
      'session.next.step.failed',
  SessionNextStepFailedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
