// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_compaction_ended_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextCompactionEndedSyncEventData
_$SyncEventSessionNextCompactionEndedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextCompactionEndedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'messageID',
      'reason',
      'text',
      'recent',
    ],
  );
  final val = SyncEventSessionNextCompactionEndedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    messageID: $checkedConvert('messageID', (v) => v as String),
    reason: $checkedConvert(
      'reason',
      (v) => $enumDecode(
        _$SyncEventSessionNextCompactionEndedSyncEventDataReasonEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextCompactionEndedSyncEventDataReasonEnum
            .unknownDefaultOpenApi,
      ),
    ),
    text: $checkedConvert('text', (v) => v as String),
    recent: $checkedConvert('recent', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextCompactionEndedSyncEventDataToJson(
  SyncEventSessionNextCompactionEndedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'reason':
      _$SyncEventSessionNextCompactionEndedSyncEventDataReasonEnumEnumMap[instance
          .reason]!,
  'text': instance.text,
  'recent': instance.recent,
};

const _$SyncEventSessionNextCompactionEndedSyncEventDataReasonEnumEnumMap = {
  SyncEventSessionNextCompactionEndedSyncEventDataReasonEnum.auto: 'auto',
  SyncEventSessionNextCompactionEndedSyncEventDataReasonEnum.manual: 'manual',
  SyncEventSessionNextCompactionEndedSyncEventDataReasonEnum
          .unknownDefaultOpenApi:
      'unknown_default_open_api',
};
