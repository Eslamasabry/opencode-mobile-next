// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_progress_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolProgressSyncEvent
_$SyncEventSessionNextToolProgressSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolProgressSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextToolProgressSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextToolProgressSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextToolProgressSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextToolProgressSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolProgressSyncEventToJson(
  SyncEventSessionNextToolProgressSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextToolProgressSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextToolProgressSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextToolProgressSyncEventTypeEnum
          .sessionPeriodNextPeriodToolPeriodProgressPeriod1:
      'session.next.tool.progress.1',
  SyncEventSessionNextToolProgressSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
