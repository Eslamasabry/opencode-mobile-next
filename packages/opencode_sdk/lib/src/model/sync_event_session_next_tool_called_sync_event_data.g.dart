// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_called_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolCalledSyncEventData
_$SyncEventSessionNextToolCalledSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolCalledSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'callID',
      'tool',
      'input',
      'provider',
    ],
  );
  final val = SyncEventSessionNextToolCalledSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    callID: $checkedConvert('callID', (v) => v as String),
    tool: $checkedConvert('tool', (v) => v as String),
    input: $checkedConvert('input', (v) => v as Object),
    provider: $checkedConvert(
      'provider',
      (v) => SyncEventSessionNextToolCalledSyncEventDataProvider.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolCalledSyncEventDataToJson(
  SyncEventSessionNextToolCalledSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'callID': instance.callID,
  'tool': instance.tool,
  'input': instance.input,
  'provider': instance.provider.toJson(),
};
