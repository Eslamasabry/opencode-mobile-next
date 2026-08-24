// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_progress_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolProgressSyncEventData
_$SyncEventSessionNextToolProgressSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolProgressSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'callID',
      'structured',
      'content',
    ],
  );
  final val = SyncEventSessionNextToolProgressSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    callID: $checkedConvert('callID', (v) => v as String),
    structured: $checkedConvert('structured', (v) => v as Object),
    content: $checkedConvert(
      'content',
      (v) => (v as List<dynamic>).map(LLMToolContent.fromJson).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolProgressSyncEventDataToJson(
  SyncEventSessionNextToolProgressSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'callID': instance.callID,
  'structured': instance.structured,
  'content': instance.content.map((e) => e.toJson()).toList(),
};
