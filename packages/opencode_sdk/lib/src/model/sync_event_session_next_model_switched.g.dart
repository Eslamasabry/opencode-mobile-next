// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_model_switched.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextModelSwitched _$SyncEventSessionNextModelSwitchedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextModelSwitched', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextModelSwitched(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextModelSwitchedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextModelSwitchedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextModelSwitchedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextModelSwitchedToJson(
  SyncEventSessionNextModelSwitched instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextModelSwitchedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextModelSwitchedTypeEnumEnumMap = {
  SyncEventSessionNextModelSwitchedTypeEnum.sync_: 'sync',
  SyncEventSessionNextModelSwitchedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
