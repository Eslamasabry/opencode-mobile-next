// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_model_switched_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextModelSwitchedSyncEvent
_$SyncEventSessionNextModelSwitchedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextModelSwitchedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextModelSwitchedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextModelSwitchedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextModelSwitchedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextModelSwitchedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextModelSwitchedSyncEventToJson(
  SyncEventSessionNextModelSwitchedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextModelSwitchedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextModelSwitchedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextModelSwitchedSyncEventTypeEnum
          .sessionPeriodNextPeriodModelPeriodSwitchedPeriod1:
      'session.next.model.switched.1',
  SyncEventSessionNextModelSwitchedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
