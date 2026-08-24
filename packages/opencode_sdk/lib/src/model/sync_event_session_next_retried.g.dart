// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_retried.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRetried _$SyncEventSessionNextRetriedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextRetried', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextRetried(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextRetriedTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextRetriedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextRetriedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextRetriedToJson(
  SyncEventSessionNextRetried instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextRetriedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextRetriedTypeEnumEnumMap = {
  SyncEventSessionNextRetriedTypeEnum.sync_: 'sync',
  SyncEventSessionNextRetriedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
