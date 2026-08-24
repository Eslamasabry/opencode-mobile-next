// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_revert_committed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextRevertCommitted _$SessionNextRevertCommittedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextRevertCommitted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextRevertCommitted(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextRevertCommittedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextRevertCommittedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextRevertCommittedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextRevertCommittedToJson(
  SessionNextRevertCommitted instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextRevertCommittedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextRevertCommittedTypeEnumEnumMap = {
  SessionNextRevertCommittedTypeEnum
          .sessionPeriodNextPeriodRevertPeriodCommitted:
      'session.next.revert.committed',
  SessionNextRevertCommittedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
