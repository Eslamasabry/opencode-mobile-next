// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_compaction_delta.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextCompactionDelta _$SessionNextCompactionDeltaFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextCompactionDelta', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextCompactionDelta(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextCompactionDeltaTypeEnumEnumMap,
        v,
        unknownValue: SessionNextCompactionDeltaTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextContextUpdatedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextCompactionDeltaToJson(
  SessionNextCompactionDelta instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextCompactionDeltaTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextCompactionDeltaTypeEnumEnumMap = {
  SessionNextCompactionDeltaTypeEnum
          .sessionPeriodNextPeriodCompactionPeriodDelta:
      'session.next.compaction.delta',
  SessionNextCompactionDeltaTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
