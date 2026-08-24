// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_agent_switched_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextAgentSwitchedSyncEventData
_$SyncEventSessionNextAgentSwitchedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextAgentSwitchedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['timestamp', 'sessionID', 'messageID', 'agent'],
  );
  final val = SyncEventSessionNextAgentSwitchedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    messageID: $checkedConvert('messageID', (v) => v as String),
    agent: $checkedConvert('agent', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextAgentSwitchedSyncEventDataToJson(
  SyncEventSessionNextAgentSwitchedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'agent': instance.agent,
};
