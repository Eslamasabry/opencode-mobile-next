// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_shell_started_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextShellStartedSyncEventData
_$SyncEventSessionNextShellStartedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextShellStartedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'messageID',
      'callID',
      'command',
    ],
  );
  final val = SyncEventSessionNextShellStartedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    messageID: $checkedConvert('messageID', (v) => v as String),
    callID: $checkedConvert('callID', (v) => v as String),
    command: $checkedConvert('command', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextShellStartedSyncEventDataToJson(
  SyncEventSessionNextShellStartedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'callID': instance.callID,
  'command': instance.command,
};
