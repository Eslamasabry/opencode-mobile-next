// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_model_switched_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextModelSwitchedSyncEventData
_$SyncEventSessionNextModelSwitchedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextModelSwitchedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['timestamp', 'sessionID', 'messageID', 'model'],
  );
  final val = SyncEventSessionNextModelSwitchedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    messageID: $checkedConvert('messageID', (v) => v as String),
    model: $checkedConvert(
      'model',
      (v) => ModelRef.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextModelSwitchedSyncEventDataToJson(
  SyncEventSessionNextModelSwitchedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'model': instance.model.toJson(),
};
