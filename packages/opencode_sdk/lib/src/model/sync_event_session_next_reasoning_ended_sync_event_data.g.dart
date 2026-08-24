// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_reasoning_ended_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextReasoningEndedSyncEventData
_$SyncEventSessionNextReasoningEndedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextReasoningEndedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'reasoningID',
      'text',
    ],
  );
  final val = SyncEventSessionNextReasoningEndedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    reasoningID: $checkedConvert('reasoningID', (v) => v as String),
    text: $checkedConvert('text', (v) => v as String),
    providerMetadata: $checkedConvert(
      'providerMetadata',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as Object)),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextReasoningEndedSyncEventDataToJson(
  SyncEventSessionNextReasoningEndedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'reasoningID': instance.reasoningID,
  'text': instance.text,
  'providerMetadata': ?instance.providerMetadata,
};
