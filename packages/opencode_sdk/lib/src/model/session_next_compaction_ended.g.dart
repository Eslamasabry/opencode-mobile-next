// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_compaction_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextCompactionEnded _$SessionNextCompactionEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextCompactionEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextCompactionEnded(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextCompactionEndedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextCompactionEndedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextCompactionEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextCompactionEndedToJson(
  SessionNextCompactionEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextCompactionEndedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextCompactionEndedTypeEnumEnumMap = {
  SessionNextCompactionEndedTypeEnum
          .sessionPeriodNextPeriodCompactionPeriodEnded:
      'session.next.compaction.ended',
  SessionNextCompactionEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
