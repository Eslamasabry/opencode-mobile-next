// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_input_started_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolInputStartedSyncEvent
_$SyncEventSessionNextToolInputStartedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolInputStartedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextToolInputStartedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextToolInputStartedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextToolInputStartedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextToolInputStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolInputStartedSyncEventToJson(
  SyncEventSessionNextToolInputStartedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextToolInputStartedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextToolInputStartedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextToolInputStartedSyncEventTypeEnum
          .sessionPeriodNextPeriodToolPeriodInputPeriodStartedPeriod1:
      'session.next.tool.input.started.1',
  SyncEventSessionNextToolInputStartedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
