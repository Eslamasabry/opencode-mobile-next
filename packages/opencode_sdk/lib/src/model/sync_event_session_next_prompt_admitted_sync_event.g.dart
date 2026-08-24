// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_prompt_admitted_sync_event.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextPromptAdmittedSyncEvent
_$SyncEventSessionNextPromptAdmittedSyncEventFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextPromptAdmittedSyncEvent', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['type', 'id', 'seq', 'aggregateID', 'data'],
  );
  final val = SyncEventSessionNextPromptAdmittedSyncEvent(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextPromptAdmittedSyncEventTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextPromptAdmittedSyncEventTypeEnum
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

Map<String, dynamic> _$SyncEventSessionNextPromptAdmittedSyncEventToJson(
  SyncEventSessionNextPromptAdmittedSyncEvent instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextPromptAdmittedSyncEventTypeEnumEnumMap[instance
          .type]!,
  'id': instance.id,
  'seq': instance.seq,
  'aggregateID': instance.aggregateID,
  'data': instance.data.toJson(),
};

const _$SyncEventSessionNextPromptAdmittedSyncEventTypeEnumEnumMap = {
  SyncEventSessionNextPromptAdmittedSyncEventTypeEnum
          .sessionPeriodNextPeriodPromptPeriodAdmittedPeriod1:
      'session.next.prompt.admitted.1',
  SyncEventSessionNextPromptAdmittedSyncEventTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
