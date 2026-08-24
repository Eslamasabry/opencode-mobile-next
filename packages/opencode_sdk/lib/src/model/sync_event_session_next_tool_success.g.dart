// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_success.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolSuccess _$SyncEventSessionNextToolSuccessFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolSuccess', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextToolSuccess(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextToolSuccessTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextToolSuccessTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextToolSuccessSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolSuccessToJson(
  SyncEventSessionNextToolSuccess instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextToolSuccessTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextToolSuccessTypeEnumEnumMap = {
  SyncEventSessionNextToolSuccessTypeEnum.sync_: 'sync',
  SyncEventSessionNextToolSuccessTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
