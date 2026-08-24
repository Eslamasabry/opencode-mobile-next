// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_part_removed_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessagePartRemovedSyncEventData
_$SyncEventMessagePartRemovedSyncEventDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventMessagePartRemovedSyncEventData', json, (
      $checkedConvert,
    ) {
      $checkKeys(
        json,
        requiredKeys: const ['sessionID', 'messageID', 'partID'],
      );
      final val = SyncEventMessagePartRemovedSyncEventData(
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        partID: $checkedConvert('partID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventMessagePartRemovedSyncEventDataToJson(
  SyncEventMessagePartRemovedSyncEventData instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'partID': instance.partID,
};
