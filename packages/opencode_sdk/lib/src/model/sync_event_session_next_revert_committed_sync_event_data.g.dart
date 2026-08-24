// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_revert_committed_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRevertCommittedSyncEventData
_$SyncEventSessionNextRevertCommittedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextRevertCommittedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['timestamp', 'sessionID', 'messageID']);
  final val = SyncEventSessionNextRevertCommittedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    messageID: $checkedConvert('messageID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextRevertCommittedSyncEventDataToJson(
  SyncEventSessionNextRevertCommittedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
};
