// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_compaction_ended_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextCompactionEndedSyncEvent
_$SyncEventSessionNextCompactionEndedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextCompactionEndedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextCompactionEndedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextCompactionEndedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextCompactionEndedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextCompactionEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextCompactionEndedSyncEventToJson(
  SyncEventSessionNextCompactionEndedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextCompactionEndedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextCompactionEndedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextCompactionEndedSyncEventTypeEnum
          .sessionPeriodNextPeriodCompactionPeriodEndedPeriod1:
      'session.next.compaction.ended.1',
  SyncEventSessionNextCompactionEndedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
