// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_step_failed_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextStepFailedSyncEventData
_$SyncEventSessionNextStepFailedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextStepFailedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'error',
    ],
  );
  final val = SyncEventSessionNextStepFailedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    error: $checkedConvert(
      'error',
      (v) => SessionErrorUnknown.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextStepFailedSyncEventDataToJson(
  SyncEventSessionNextStepFailedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'error': instance.error.toJson(),
};
