// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_step_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextStepStarted _$SessionNextStepStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextStepStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextStepStarted(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextStepStartedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextStepStartedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextStepStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextStepStartedToJson(
  SessionNextStepStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextStepStartedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextStepStartedTypeEnumEnumMap = {
  SessionNextStepStartedTypeEnum.sessionPeriodNextPeriodStepPeriodStarted:
      'session.next.step.started',
  SessionNextStepStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
