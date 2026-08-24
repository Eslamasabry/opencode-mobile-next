// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_reasoning_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextReasoningStarted _$SessionNextReasoningStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextReasoningStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextReasoningStarted(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextReasoningStartedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextReasoningStartedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextReasoningStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextReasoningStartedToJson(
  SessionNextReasoningStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextReasoningStartedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextReasoningStartedTypeEnumEnumMap = {
  SessionNextReasoningStartedTypeEnum
          .sessionPeriodNextPeriodReasoningPeriodStarted:
      'session.next.reasoning.started',
  SessionNextReasoningStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
