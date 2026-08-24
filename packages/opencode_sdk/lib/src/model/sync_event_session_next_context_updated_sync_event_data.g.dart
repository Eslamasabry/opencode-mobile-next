// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_context_updated_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextContextUpdatedSyncEventData
_$SyncEventSessionNextContextUpdatedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextContextUpdatedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['timestamp', 'sessionID', 'messageID', 'text'],
  );
  final val = SyncEventSessionNextContextUpdatedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    messageID: $checkedConvert('messageID', (v) => v as String),
    text: $checkedConvert('text', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextContextUpdatedSyncEventDataToJson(
  SyncEventSessionNextContextUpdatedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'text': instance.text,
};
