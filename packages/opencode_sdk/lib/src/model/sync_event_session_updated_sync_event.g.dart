// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_updated_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionUpdatedSyncEvent _$SyncEventSessionUpdatedSyncEventFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('SyncEventSessionUpdatedSyncEvent', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionUpdatedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionUpdatedSyncEventTypeEnumEnumMap,
            v,
            unknownValue:
                SyncEventSessionUpdatedSyncEventTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionCreatedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionUpdatedSyncEventToJson(
  SyncEventSessionUpdatedSyncEvent instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionUpdatedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionUpdatedSyncEventTypeEnumEnumMap = {
  SyncEventSessionUpdatedSyncEventTypeEnum.sessionPeriodUpdatedPeriod1:
      'session.updated.1',
  SyncEventSessionUpdatedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
