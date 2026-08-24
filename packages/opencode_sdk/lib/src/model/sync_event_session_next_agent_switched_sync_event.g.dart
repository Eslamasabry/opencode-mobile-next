// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_agent_switched_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextAgentSwitchedSyncEvent
_$SyncEventSessionNextAgentSwitchedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextAgentSwitchedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextAgentSwitchedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextAgentSwitchedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextAgentSwitchedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextAgentSwitchedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextAgentSwitchedSyncEventToJson(
  SyncEventSessionNextAgentSwitchedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextAgentSwitchedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextAgentSwitchedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextAgentSwitchedSyncEventTypeEnum
          .sessionPeriodNextPeriodAgentPeriodSwitchedPeriod1:
      'session.next.agent.switched.1',
  SyncEventSessionNextAgentSwitchedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
