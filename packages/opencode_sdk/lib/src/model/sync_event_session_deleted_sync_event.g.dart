// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_deleted_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionDeletedSyncEvent _$SyncEventSessionDeletedSyncEventFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('SyncEventSessionDeletedSyncEvent', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionDeletedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionDeletedSyncEventTypeEnumEnumMap,
            v,
            unknownValue:
                SyncEventSessionDeletedSyncEventTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$SyncEventSessionDeletedSyncEventToJson(
  SyncEventSessionDeletedSyncEvent instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionDeletedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionDeletedSyncEventTypeEnumEnumMap = {
  SyncEventSessionDeletedSyncEventTypeEnum.sessionPeriodDeletedPeriod1:
      'session.deleted.1',
  SyncEventSessionDeletedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
