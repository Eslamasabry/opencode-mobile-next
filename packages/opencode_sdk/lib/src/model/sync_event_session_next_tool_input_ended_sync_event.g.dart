// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_input_ended_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolInputEndedSyncEvent
_$SyncEventSessionNextToolInputEndedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolInputEndedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextToolInputEndedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextToolInputEndedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextToolInputEndedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextToolInputEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolInputEndedSyncEventToJson(
  SyncEventSessionNextToolInputEndedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextToolInputEndedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextToolInputEndedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextToolInputEndedSyncEventTypeEnum
          .sessionPeriodNextPeriodToolPeriodInputPeriodEndedPeriod1:
      'session.next.tool.input.ended.1',
  SyncEventSessionNextToolInputEndedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
