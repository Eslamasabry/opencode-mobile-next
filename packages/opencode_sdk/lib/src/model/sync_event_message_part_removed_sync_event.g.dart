// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_part_removed_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessagePartRemovedSyncEvent
_$SyncEventMessagePartRemovedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventMessagePartRemovedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventMessagePartRemovedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventMessagePartRemovedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventMessagePartRemovedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventMessagePartRemovedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventMessagePartRemovedSyncEventToJson(
  SyncEventMessagePartRemovedSyncEvent instance,
) => <String, dynamic>{
  'type': _$SyncEventMessagePartRemovedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventMessagePartRemovedSyncEventTypeEnumEnumMap = {
  SyncEventMessagePartRemovedSyncEventTypeEnum
          .messagePeriodPartPeriodRemovedPeriod1:
      'message.part.removed.1',
  SyncEventMessagePartRemovedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
