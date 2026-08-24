// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_reasoning_started_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextReasoningStartedSyncEventData
_$SyncEventSessionNextReasoningStartedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextReasoningStartedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'reasoningID',
    ],
  );
  final val = SyncEventSessionNextReasoningStartedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    reasoningID: $checkedConvert('reasoningID', (v) => v as String),
    providerMetadata: $checkedConvert(
      'providerMetadata',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as Object)),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextReasoningStartedSyncEventDataToJson(
  SyncEventSessionNextReasoningStartedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'reasoningID': instance.reasoningID,
  'providerMetadata': ?instance.providerMetadata,
};
