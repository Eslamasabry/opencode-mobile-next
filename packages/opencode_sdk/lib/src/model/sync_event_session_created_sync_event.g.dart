// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_created_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionCreatedSyncEvent _$SyncEventSessionCreatedSyncEventFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('SyncEventSessionCreatedSyncEvent', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionCreatedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionCreatedSyncEventTypeEnumEnumMap,
            v,
            unknownValue:
                SyncEventSessionCreatedSyncEventTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$SyncEventSessionCreatedSyncEventToJson(
  SyncEventSessionCreatedSyncEvent instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionCreatedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionCreatedSyncEventTypeEnumEnumMap = {
  SyncEventSessionCreatedSyncEventTypeEnum.sessionPeriodCreatedPeriod1:
      'session.created.1',
  SyncEventSessionCreatedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
