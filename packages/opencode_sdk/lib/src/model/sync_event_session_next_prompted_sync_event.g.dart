// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_prompted_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextPromptedSyncEvent
_$SyncEventSessionNextPromptedSyncEventFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextPromptedSyncEvent', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
      );
      final val = SyncEventSessionNextPromptedSyncEvent(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextPromptedSyncEventTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextPromptedSyncEventTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        seq: $checkedConvert('seq', (v) => v as num),
        aggregateID: $checkedConvert('aggregateID', (v) => v as String),
        data: $checkedConvert(
          'data',
          (v) => SyncEventSessionNextPromptedSyncEventData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextPromptedSyncEventToJson(
  SyncEventSessionNextPromptedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextPromptedSyncEventTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextPromptedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextPromptedSyncEventTypeEnum
          .sessionPeriodNextPeriodPromptedPeriod1:
      'session.next.prompted.1',
  SyncEventSessionNextPromptedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
