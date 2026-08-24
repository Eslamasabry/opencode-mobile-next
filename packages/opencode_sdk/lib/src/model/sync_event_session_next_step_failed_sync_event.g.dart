// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_step_failed_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextStepFailedSyncEvent
_$SyncEventSessionNextStepFailedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextStepFailedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextStepFailedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextStepFailedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextStepFailedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextStepFailedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextStepFailedSyncEventToJson(
  SyncEventSessionNextStepFailedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextStepFailedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextStepFailedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextStepFailedSyncEventTypeEnum
          .sessionPeriodNextPeriodStepPeriodFailedPeriod2:
      'session.next.step.failed.2',
  SyncEventSessionNextStepFailedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
