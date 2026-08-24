// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_agent_switched.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextAgentSwitched _$SyncEventSessionNextAgentSwitchedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextAgentSwitched', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextAgentSwitched(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextAgentSwitchedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextAgentSwitchedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextAgentSwitchedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextAgentSwitchedToJson(
  SyncEventSessionNextAgentSwitched instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextAgentSwitchedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextAgentSwitchedTypeEnumEnumMap = {
  SyncEventSessionNextAgentSwitchedTypeEnum.sync_: 'sync',
  SyncEventSessionNextAgentSwitchedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
