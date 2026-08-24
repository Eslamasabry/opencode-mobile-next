// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionUpdated _$SyncEventSessionUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionUpdated(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionUpdatedTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) =>
          SyncEventSessionUpdatedSyncEvent.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionUpdatedToJson(
  SyncEventSessionUpdated instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionUpdatedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionUpdatedTypeEnumEnumMap = {
  SyncEventSessionUpdatedTypeEnum.sync_: 'sync',
  SyncEventSessionUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
