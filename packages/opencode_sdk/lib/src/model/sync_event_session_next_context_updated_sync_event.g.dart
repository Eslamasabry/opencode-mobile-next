// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_context_updated_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextContextUpdatedSyncEvent
_$SyncEventSessionNextContextUpdatedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextContextUpdatedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextContextUpdatedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextContextUpdatedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextContextUpdatedSyncEventTypeEnum
            .unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    seq: $checkedConvert('seq', (v) => v as num),
    aggregateID: $checkedConvert('aggregateID', (v) => v as String),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextContextUpdatedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextContextUpdatedSyncEventToJson(
  SyncEventSessionNextContextUpdatedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextContextUpdatedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextContextUpdatedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextContextUpdatedSyncEventTypeEnum
          .sessionPeriodNextPeriodContextPeriodUpdatedPeriod1:
      'session.next.context.updated.1',
  SyncEventSessionNextContextUpdatedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
