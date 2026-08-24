// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_revert_staged_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRevertStagedSyncEventData
_$SyncEventSessionNextRevertStagedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextRevertStagedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['timestamp', 'sessionID', 'revert']);
  final val = SyncEventSessionNextRevertStagedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    revert: $checkedConvert(
      'revert',
      (v) => RevertState.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextRevertStagedSyncEventDataToJson(
  SyncEventSessionNextRevertStagedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'revert': instance.revert.toJson(),
};
