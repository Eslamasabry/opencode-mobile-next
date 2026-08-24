// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_step_started_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextStepStartedSyncEvent
_$SyncEventSessionNextStepStartedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextStepStartedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextStepStartedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextStepStartedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextStepStartedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextStepStartedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextStepStartedSyncEventToJson(
  SyncEventSessionNextStepStartedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextStepStartedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextStepStartedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextStepStartedSyncEventTypeEnum
          .sessionPeriodNextPeriodStepPeriodStartedPeriod1:
      'session.next.step.started.1',
  SyncEventSessionNextStepStartedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
