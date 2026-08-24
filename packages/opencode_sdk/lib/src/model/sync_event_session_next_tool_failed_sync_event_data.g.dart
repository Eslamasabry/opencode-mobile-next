// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_failed_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolFailedSyncEventData
_$SyncEventSessionNextToolFailedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextToolFailedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'assistantMessageID',
      'callID',
      'error',
      'provider',
    ],
  );
  final val = SyncEventSessionNextToolFailedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    assistantMessageID: $checkedConvert(
      'assistantMessageID',
      (v) => v as String,
    ),
    callID: $checkedConvert('callID', (v) => v as String),
    error: $checkedConvert(
      'error',
      (v) => SessionErrorUnknown.fromJson(v as Map<String, dynamic>),
    ),
    result: $checkedConvert('result', (v) => v),
    provider: $checkedConvert(
      'provider',
      (v) => SyncEventSessionNextToolCalledSyncEventDataProvider.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextToolFailedSyncEventDataToJson(
  SyncEventSessionNextToolFailedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'assistantMessageID': instance.assistantMessageID,
  'callID': instance.callID,
  'error': instance.error.toJson(),
  'result': ?instance.result,
  'provider': instance.provider.toJson(),
};
