// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_reasoning_started_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextReasoningStartedSyncEvent
_$SyncEventSessionNextReasoningStartedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextReasoningStartedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextReasoningStartedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextReasoningStartedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextReasoningStartedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextReasoningStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextReasoningStartedSyncEventToJson(
  SyncEventSessionNextReasoningStartedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextReasoningStartedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextReasoningStartedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextReasoningStartedSyncEventTypeEnum
          .sessionPeriodNextPeriodReasoningPeriodStartedPeriod1:
      'session.next.reasoning.started.1',
  SyncEventSessionNextReasoningStartedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
