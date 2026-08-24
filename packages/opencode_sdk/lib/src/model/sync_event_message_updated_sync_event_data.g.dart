// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_updated_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessageUpdatedSyncEventData
_$SyncEventMessageUpdatedSyncEventDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventMessageUpdatedSyncEventData', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['sessionID', 'info']);
      final val = SyncEventMessageUpdatedSyncEventData(
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        info: $checkedConvert('info', (v) => Message.fromJson(v)),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventMessageUpdatedSyncEventDataToJson(
  SyncEventMessageUpdatedSyncEventData instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'info': instance.info.toJson(),
};
