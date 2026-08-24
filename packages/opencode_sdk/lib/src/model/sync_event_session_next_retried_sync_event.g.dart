// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_retried_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRetriedSyncEvent
_$SyncEventSessionNextRetriedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextRetriedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextRetriedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextRetriedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextRetriedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextRetriedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextRetriedSyncEventToJson(
  SyncEventSessionNextRetriedSyncEvent instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextRetriedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextRetriedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextRetriedSyncEventTypeEnum
          .sessionPeriodNextPeriodRetriedPeriod1:
      'session.next.retried.1',
  SyncEventSessionNextRetriedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
