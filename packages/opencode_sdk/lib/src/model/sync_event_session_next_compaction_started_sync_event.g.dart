// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_compaction_started_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextCompactionStartedSyncEvent
_$SyncEventSessionNextCompactionStartedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextCompactionStartedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextCompactionStartedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextCompactionStartedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextCompactionStartedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextCompactionStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextCompactionStartedSyncEventToJson(
  SyncEventSessionNextCompactionStartedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextCompactionStartedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextCompactionStartedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextCompactionStartedSyncEventTypeEnum
          .sessionPeriodNextPeriodCompactionPeriodStartedPeriod1:
      'session.next.compaction.started.1',
  SyncEventSessionNextCompactionStartedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
