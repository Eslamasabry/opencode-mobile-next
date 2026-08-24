// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_revert_cleared_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRevertClearedSyncEventData
_$SyncEventSessionNextRevertClearedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextRevertClearedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['timestamp', 'sessionID']);
  final val = SyncEventSessionNextRevertClearedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextRevertClearedSyncEventDataToJson(
  SyncEventSessionNextRevertClearedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
};
