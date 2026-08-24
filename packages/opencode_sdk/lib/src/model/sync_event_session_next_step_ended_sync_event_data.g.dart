// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_step_ended_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextStepEndedSyncEventData
_$SyncEventSessionNextStepEndedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextStepEndedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'finish',
      'cost',
      'tokens',
    ],
  );
  final val = SyncEventSessionNextStepEndedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    finish: $checkedConvert('finish', (v) => v as String),
    cost: $checkedConvert('cost', (v) => v as num),
    tokens: $checkedConvert(
      'tokens',
      (v) => SessionTokens.fromJson(v as Map<String, dynamic>),
    ),
    snapshot: $checkedConvert('snapshot', (v) => v as String?),
    files: $checkedConvert(
      'files',
      (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextStepEndedSyncEventDataToJson(
  SyncEventSessionNextStepEndedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'finish': instance.finish,
  'cost': instance.cost,
  'tokens': instance.tokens.toJson(),
  'snapshot': ?instance.snapshot,
  'files': ?instance.files,
};
