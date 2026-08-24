// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_retried_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRetriedSyncEventData
_$SyncEventSessionNextRetriedSyncEventDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextRetriedSyncEventData', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['timestamp', 'sessionID', 'attempt', 'error'],
      );
      final val = SyncEventSessionNextRetriedSyncEventData(
        timestamp: $checkedConvert('timestamp', (v) => v as num),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        attempt: $checkedConvert('attempt', (v) => v as num),
        error: $checkedConvert(
          'error',
          (v) => SessionNextRetryError.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextRetriedSyncEventDataToJson(
  SyncEventSessionNextRetriedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'attempt': instance.attempt,
  'error': instance.error.toJson(),
};
