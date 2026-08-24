// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_reasoning_ended_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextReasoningEndedSyncEvent
_$SyncEventSessionNextReasoningEndedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextReasoningEndedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextReasoningEndedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextReasoningEndedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextReasoningEndedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextReasoningEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextReasoningEndedSyncEventToJson(
  SyncEventSessionNextReasoningEndedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextReasoningEndedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextReasoningEndedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextReasoningEndedSyncEventTypeEnum
          .sessionPeriodNextPeriodReasoningPeriodEndedPeriod1:
      'session.next.reasoning.ended.1',
  SyncEventSessionNextReasoningEndedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
