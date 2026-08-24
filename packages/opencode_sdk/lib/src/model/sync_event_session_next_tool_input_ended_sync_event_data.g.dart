// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_input_ended_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolInputEndedSyncEventData
_$SyncEventSessionNextToolInputEndedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolInputEndedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'callID',
      'text',
    ],
  );
  final val = SyncEventSessionNextToolInputEndedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    callID: $checkedConvert('callID', (v) => v as String),
    text: $checkedConvert('text', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolInputEndedSyncEventDataToJson(
  SyncEventSessionNextToolInputEndedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'callID': instance.callID,
  'text': instance.text,
};
