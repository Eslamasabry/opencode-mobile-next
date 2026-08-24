// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_text_ended_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextTextEndedSyncEvent
_$SyncEventSessionNextTextEndedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextTextEndedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextTextEndedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextTextEndedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextTextEndedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextTextEndedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextTextEndedSyncEventToJson(
  SyncEventSessionNextTextEndedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextTextEndedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextTextEndedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextTextEndedSyncEventTypeEnum
          .sessionPeriodNextPeriodTextPeriodEndedPeriod1:
      'session.next.text.ended.1',
  SyncEventSessionNextTextEndedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
