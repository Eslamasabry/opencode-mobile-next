// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_removed_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessageRemovedSyncEventData
_$SyncEventMessageRemovedSyncEventDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventMessageRemovedSyncEventData', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['sessionID', 'messageID']);
      final val = SyncEventMessageRemovedSyncEventData(
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventMessageRemovedSyncEventDataToJson(
  SyncEventMessageRemovedSyncEventData instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
};
