// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_step_ended_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextStepEndedSyncEvent
_$SyncEventSessionNextStepEndedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextStepEndedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextStepEndedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextStepEndedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextStepEndedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextStepEndedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextStepEndedSyncEventToJson(
  SyncEventSessionNextStepEndedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextStepEndedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextStepEndedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextStepEndedSyncEventTypeEnum
          .sessionPeriodNextPeriodStepPeriodEndedPeriod2:
      'session.next.step.ended.2',
  SyncEventSessionNextStepEndedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
