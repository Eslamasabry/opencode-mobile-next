// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_revert_cleared_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRevertClearedSyncEvent
_$SyncEventSessionNextRevertClearedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextRevertClearedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextRevertClearedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextRevertClearedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextRevertClearedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextRevertClearedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextRevertClearedSyncEventToJson(
  SyncEventSessionNextRevertClearedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextRevertClearedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextRevertClearedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextRevertClearedSyncEventTypeEnum
          .sessionPeriodNextPeriodRevertPeriodClearedPeriod1:
      'session.next.revert.cleared.1',
  SyncEventSessionNextRevertClearedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
