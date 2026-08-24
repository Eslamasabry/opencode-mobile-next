// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_text_started_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextTextStartedSyncEvent
_$SyncEventSessionNextTextStartedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextTextStartedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextTextStartedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextTextStartedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextTextStartedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextTextStartedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextTextStartedSyncEventToJson(
  SyncEventSessionNextTextStartedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextTextStartedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextTextStartedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextTextStartedSyncEventTypeEnum
          .sessionPeriodNextPeriodTextPeriodStartedPeriod1:
      'session.next.text.started.1',
  SyncEventSessionNextTextStartedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
