// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_part_updated_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessagePartUpdatedSyncEventData
_$SyncEventMessagePartUpdatedSyncEventDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventMessagePartUpdatedSyncEventData', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['sessionID', 'part', 'time']);
      final val = SyncEventMessagePartUpdatedSyncEventData(
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        part_: $checkedConvert('part', (v) => ModelPart.fromJson(v)),
        time: $checkedConvert('time', (v) => v as num),
      );
      return val;
    }, fieldKeyMap: const {'part_': 'part'});

Map<String, dynamic> _$SyncEventMessagePartUpdatedSyncEventDataToJson(
  SyncEventMessagePartUpdatedSyncEventData instance,
) => <String, dynamic>{
  'sessionID': instance.sessionID,
  'part': instance.part_.toJson(),
  'time': instance.time,
};
