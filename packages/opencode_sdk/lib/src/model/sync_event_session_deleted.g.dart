// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_deleted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionDeleted _$SyncEventSessionDeletedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionDeleted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionDeleted(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionDeletedTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionDeletedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) =>
          SyncEventSessionDeletedSyncEvent.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionDeletedToJson(
  SyncEventSessionDeleted instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionDeletedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionDeletedTypeEnumEnumMap = {
  SyncEventSessionDeletedTypeEnum.sync_: 'sync',
  SyncEventSessionDeletedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
