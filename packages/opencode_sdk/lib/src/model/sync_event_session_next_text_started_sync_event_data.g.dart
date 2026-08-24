// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_text_started_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextTextStartedSyncEventData
_$SyncEventSessionNextTextStartedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextTextStartedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'textID',
    ],
  );
  final val = SyncEventSessionNextTextStartedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    textID: $checkedConvert('textID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextTextStartedSyncEventDataToJson(
  SyncEventSessionNextTextStartedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'textID': instance.textID,
};
