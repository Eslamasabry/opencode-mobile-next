// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_moved_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextMovedSyncEventData
_$SyncEventSessionNextMovedSyncEventDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextMovedSyncEventData', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['timestamp', 'sessionID', 'location'],
      );
      final val = SyncEventSessionNextMovedSyncEventData(
        timestamp: $checkedConvert('timestamp', (v) => v as num),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        location: $checkedConvert(
          'location',
          (v) => LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        subdirectory: $checkedConvert('subdirectory', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextMovedSyncEventDataToJson(
  SyncEventSessionNextMovedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'location': instance.location.toJson(),
  'subdirectory': ?instance.subdirectory,
};
