// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_removed_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessageRemovedSyncEvent _$SyncEventMessageRemovedSyncEventFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('SyncEventMessageRemovedSyncEvent', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventMessageRemovedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventMessageRemovedSyncEventTypeEnumEnumMap,
            v,
            unknownValue:
                SyncEventMessageRemovedSyncEventTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventMessageRemovedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventMessageRemovedSyncEventToJson(
  SyncEventMessageRemovedSyncEvent instance,
) => <String, dynamic>{
  'type': _$SyncEventMessageRemovedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventMessageRemovedSyncEventTypeEnumEnumMap = {
  SyncEventMessageRemovedSyncEventTypeEnum.messagePeriodRemovedPeriod1:
      'message.removed.1',
  SyncEventMessageRemovedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
