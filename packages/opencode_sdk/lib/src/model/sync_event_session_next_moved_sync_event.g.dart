// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_moved_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextMovedSyncEvent _$SyncEventSessionNextMovedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextMovedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextMovedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextMovedSyncEventTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextMovedSyncEventTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextMovedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextMovedSyncEventToJson(
  SyncEventSessionNextMovedSyncEvent instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextMovedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextMovedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextMovedSyncEventTypeEnum
          .sessionPeriodNextPeriodMovedPeriod1:
      'session.next.moved.1',
  SyncEventSessionNextMovedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
