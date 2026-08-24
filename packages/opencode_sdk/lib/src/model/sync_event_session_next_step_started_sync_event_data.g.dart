// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_step_started_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextStepStartedSyncEventData
_$SyncEventSessionNextStepStartedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextStepStartedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'agent',
      'model',
    ],
  );
  final val = SyncEventSessionNextStepStartedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    agent: $checkedConvert('agent', (v) => v as String),
    model: $checkedConvert(
      'model',
      (v) => ModelRef.fromJson(v as Map<String, dynamic>),
    ),
    snapshot: $checkedConvert('snapshot', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextStepStartedSyncEventDataToJson(
  SyncEventSessionNextStepStartedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'agent': instance.agent,
  'model': instance.model.toJson(),
  'snapshot': ?instance.snapshot,
};
