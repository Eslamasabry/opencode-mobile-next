// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_revert_committed_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRevertCommittedSyncEvent
_$SyncEventSessionNextRevertCommittedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextRevertCommittedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextRevertCommittedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextRevertCommittedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextRevertCommittedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextRevertCommittedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextRevertCommittedSyncEventToJson(
  SyncEventSessionNextRevertCommittedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextRevertCommittedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextRevertCommittedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextRevertCommittedSyncEventTypeEnum
          .sessionPeriodNextPeriodRevertPeriodCommittedPeriod1:
      'session.next.revert.committed.1',
  SyncEventSessionNextRevertCommittedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
