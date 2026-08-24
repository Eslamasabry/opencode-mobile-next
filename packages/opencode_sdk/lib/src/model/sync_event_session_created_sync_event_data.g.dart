// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_created_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionCreatedSyncEventData
_$SyncEventSessionCreatedSyncEventDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionCreatedSyncEventData', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['sessionID', 'info']);
      final val = SyncEventSessionCreatedSyncEventData(
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        info: $checkedConvert(
          'info',
          (v) => Session.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionCreatedSyncEventDataToJson(
  SyncEventSessionCreatedSyncEventData instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'info': instance.info.toJson(),
};
