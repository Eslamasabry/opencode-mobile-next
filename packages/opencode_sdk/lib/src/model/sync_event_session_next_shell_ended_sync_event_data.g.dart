// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_shell_ended_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextShellEndedSyncEventData
_$SyncEventSessionNextShellEndedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextShellEndedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['timestamp', 'sessionID', 'callID', 'output'],
  );
  final val = SyncEventSessionNextShellEndedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    callID: $checkedConvert('callID', (v) => v as String),
    output: $checkedConvert('output', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextShellEndedSyncEventDataToJson(
  SyncEventSessionNextShellEndedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'callID': instance.callID,
  'output': instance.output,
};
