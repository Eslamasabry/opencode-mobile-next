// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_revert_staged_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRevertStagedSyncEvent
_$SyncEventSessionNextRevertStagedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextRevertStagedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextRevertStagedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextRevertStagedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextRevertStagedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextRevertStagedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextRevertStagedSyncEventToJson(
  SyncEventSessionNextRevertStagedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextRevertStagedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextRevertStagedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextRevertStagedSyncEventTypeEnum
          .sessionPeriodNextPeriodRevertPeriodStagedPeriod1:
      'session.next.revert.staged.1',
  SyncEventSessionNextRevertStagedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
