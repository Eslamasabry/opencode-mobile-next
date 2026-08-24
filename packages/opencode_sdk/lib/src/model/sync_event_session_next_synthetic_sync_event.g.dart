// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_synthetic_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextSyntheticSyncEvent
_$SyncEventSessionNextSyntheticSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextSyntheticSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextSyntheticSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextSyntheticSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextSyntheticSyncEventTypeEnum
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

Map<String, dynamic> _$SyncEventSessionNextSyntheticSyncEventToJson(
  SyncEventSessionNextSyntheticSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextSyntheticSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextSyntheticSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextSyntheticSyncEventTypeEnum
          .sessionPeriodNextPeriodSyntheticPeriod1:
      'session.next.synthetic.1',
  SyncEventSessionNextSyntheticSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
