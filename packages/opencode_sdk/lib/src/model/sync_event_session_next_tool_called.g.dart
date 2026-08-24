// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_called.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolCalled _$SyncEventSessionNextToolCalledFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolCalled', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextToolCalled(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextToolCalledTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextToolCalledTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextToolCalledSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolCalledToJson(
  SyncEventSessionNextToolCalled instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextToolCalledTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextToolCalledTypeEnumEnumMap = {
  SyncEventSessionNextToolCalledTypeEnum.sync_: 'sync',
  SyncEventSessionNextToolCalledTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
