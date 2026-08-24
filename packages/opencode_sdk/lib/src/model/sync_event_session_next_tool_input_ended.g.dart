// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_input_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolInputEnded _$SyncEventSessionNextToolInputEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolInputEnded', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextToolInputEnded(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextToolInputEndedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextToolInputEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextToolInputEndedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolInputEndedToJson(
  SyncEventSessionNextToolInputEnded instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextToolInputEndedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextToolInputEndedTypeEnumEnumMap = {
  SyncEventSessionNextToolInputEndedTypeEnum.sync_: 'sync',
  SyncEventSessionNextToolInputEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
