// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolFailed _$SyncEventSessionNextToolFailedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolFailed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextToolFailed(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextToolFailedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextToolFailedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextToolFailedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolFailedToJson(
  SyncEventSessionNextToolFailed instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextToolFailedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextToolFailedTypeEnumEnumMap = {
  SyncEventSessionNextToolFailedTypeEnum.sync_: 'sync',
  SyncEventSessionNextToolFailedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
