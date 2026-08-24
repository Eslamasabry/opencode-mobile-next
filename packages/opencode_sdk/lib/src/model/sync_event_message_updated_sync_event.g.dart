// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_updated_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessageUpdatedSyncEvent _$SyncEventMessageUpdatedSyncEventFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('SyncEventMessageUpdatedSyncEvent', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventMessageUpdatedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventMessageUpdatedSyncEventTypeEnumEnumMap,
            v,
            unknownValue:
                SyncEventMessageUpdatedSyncEventTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventMessageUpdatedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventMessageUpdatedSyncEventToJson(
  SyncEventMessageUpdatedSyncEvent instance,
) => <String, dynamic>{
  'type': _$SyncEventMessageUpdatedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventMessageUpdatedSyncEventTypeEnumEnumMap = {
  SyncEventMessageUpdatedSyncEventTypeEnum.messagePeriodUpdatedPeriod1:
      'message.updated.1',
  SyncEventMessageUpdatedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
