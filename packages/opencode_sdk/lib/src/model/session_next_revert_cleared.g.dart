// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_revert_cleared.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextRevertCleared _$SessionNextRevertClearedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextRevertCleared', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextRevertCleared(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextRevertClearedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextRevertClearedTypeEnum.unknownDefaultOpenApi,
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
      (v) => SyncEventSessionNextRevertClearedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextRevertClearedToJson(
  SessionNextRevertCleared instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextRevertClearedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextRevertClearedTypeEnumEnumMap = {
  SessionNextRevertClearedTypeEnum.sessionPeriodNextPeriodRevertPeriodCleared:
      'session.next.revert.cleared',
  SessionNextRevertClearedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
