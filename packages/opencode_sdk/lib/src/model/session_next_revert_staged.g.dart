// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_revert_staged.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextRevertStaged _$SessionNextRevertStagedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextRevertStaged', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextRevertStaged(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextRevertStagedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextRevertStagedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextRevertStagedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextRevertStagedToJson(
  SessionNextRevertStaged instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextRevertStagedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextRevertStagedTypeEnumEnumMap = {
  SessionNextRevertStagedTypeEnum.sessionPeriodNextPeriodRevertPeriodStaged:
      'session.next.revert.staged',
  SessionNextRevertStagedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
