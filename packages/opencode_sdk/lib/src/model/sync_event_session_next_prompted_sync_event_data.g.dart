// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_prompted_sync_event_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextPromptedSyncEventData
_$SyncEventSessionNextPromptedSyncEventDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextPromptedSyncEventData', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const [
      'timestamp',
      'sessionID',
      'messageID',
      'prompt',
      'delivery',
    ],
  );
  final val = SyncEventSessionNextPromptedSyncEventData(
    timestamp: $checkedConvert('timestamp', (v) => v as num),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    messageID: $checkedConvert('messageID', (v) => v as String),
    prompt: $checkedConvert(
      'prompt',
      (v) => Prompt.fromJson(v as Map<String, dynamic>),
    ),
    delivery: $checkedConvert(
      'delivery',
      (v) => $enumDecode(
        _$SyncEventSessionNextPromptedSyncEventDataDeliveryEnumEnumMap,
        v,
        unknownValue: SyncEventSessionNextPromptedSyncEventDataDeliveryEnum
            .unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextPromptedSyncEventDataToJson(
  SyncEventSessionNextPromptedSyncEventData instance,
) => <String, dynamic>{
  'timestamp': instance.timestamp,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'prompt': instance.prompt.toJson(),
  'delivery':
      _$SyncEventSessionNextPromptedSyncEventDataDeliveryEnumEnumMap[instance
          .delivery]!,
};

const _$SyncEventSessionNextPromptedSyncEventDataDeliveryEnumEnumMap = {
  SyncEventSessionNextPromptedSyncEventDataDeliveryEnum.steer: 'steer',
  SyncEventSessionNextPromptedSyncEventDataDeliveryEnum.queue: 'queue',
  SyncEventSessionNextPromptedSyncEventDataDeliveryEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
