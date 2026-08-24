// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_reasoning_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextReasoningEnded _$SessionNextReasoningEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextReasoningEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextReasoningEnded(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextReasoningEndedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextReasoningEndedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextReasoningEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextReasoningEndedToJson(
  SessionNextReasoningEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextReasoningEndedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextReasoningEndedTypeEnumEnumMap = {
  SessionNextReasoningEndedTypeEnum.sessionPeriodNextPeriodReasoningPeriodEnded:
      'session.next.reasoning.ended',
  SessionNextReasoningEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
