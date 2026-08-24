// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_part_updated_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessagePartUpdatedSyncEvent
_$SyncEventMessagePartUpdatedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventMessagePartUpdatedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventMessagePartUpdatedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventMessagePartUpdatedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventMessagePartUpdatedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventMessagePartUpdatedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventMessagePartUpdatedSyncEventToJson(
  SyncEventMessagePartUpdatedSyncEvent instance,
) => <String, dynamic>{
  'type': _$SyncEventMessagePartUpdatedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventMessagePartUpdatedSyncEventTypeEnumEnumMap = {
  SyncEventMessagePartUpdatedSyncEventTypeEnum
          .messagePeriodPartPeriodUpdatedPeriod1:
      'message.part.updated.1',
  SyncEventMessagePartUpdatedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
