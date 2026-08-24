// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_failed_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolFailedSyncEvent
_$SyncEventSessionNextToolFailedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextToolFailedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextToolFailedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextToolFailedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextToolFailedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextToolFailedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextToolFailedSyncEventToJson(
  SyncEventSessionNextToolFailedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextToolFailedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextToolFailedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextToolFailedSyncEventTypeEnum
          .sessionPeriodNextPeriodToolPeriodFailedPeriod1:
      'session.next.tool.failed.1',
  SyncEventSessionNextToolFailedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
