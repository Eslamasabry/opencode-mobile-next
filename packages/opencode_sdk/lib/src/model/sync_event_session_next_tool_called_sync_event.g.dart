// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_called_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolCalledSyncEvent
_$SyncEventSessionNextToolCalledSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextToolCalledSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextToolCalledSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextToolCalledSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextToolCalledSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextToolCalledSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextToolCalledSyncEventToJson(
  SyncEventSessionNextToolCalledSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextToolCalledSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextToolCalledSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextToolCalledSyncEventTypeEnum
          .sessionPeriodNextPeriodToolPeriodCalledPeriod1:
      'session.next.tool.called.1',
  SyncEventSessionNextToolCalledSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
