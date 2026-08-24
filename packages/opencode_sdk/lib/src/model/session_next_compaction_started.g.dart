// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_compaction_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextCompactionStarted _$SessionNextCompactionStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextCompactionStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextCompactionStarted(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextCompactionStartedTypeEnumEnumMap,
        v,
        unknownValue:
            SessionNextCompactionStartedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextCompactionStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextCompactionStartedToJson(
  SessionNextCompactionStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextCompactionStartedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextCompactionStartedTypeEnumEnumMap = {
  SessionNextCompactionStartedTypeEnum
          .sessionPeriodNextPeriodCompactionPeriodStarted:
      'session.next.compaction.started',
  SessionNextCompactionStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
