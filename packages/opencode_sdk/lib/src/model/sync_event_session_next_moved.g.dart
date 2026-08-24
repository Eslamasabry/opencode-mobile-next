// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_moved.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextMoved _$SyncEventSessionNextMovedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextMoved', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextMoved(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextMovedTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextMovedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextMovedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextMovedToJson(
  SyncEventSessionNextMoved instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextMovedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextMovedTypeEnumEnumMap = {
  SyncEventSessionNextMovedTypeEnum.sync_: 'sync',
  SyncEventSessionNextMovedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
