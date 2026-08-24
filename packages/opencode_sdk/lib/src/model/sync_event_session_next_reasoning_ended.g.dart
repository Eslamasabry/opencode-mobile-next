// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_reasoning_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextReasoningEnded _$SyncEventSessionNextReasoningEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextReasoningEnded', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextReasoningEnded(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextReasoningEndedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextReasoningEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextReasoningEndedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextReasoningEndedToJson(
  SyncEventSessionNextReasoningEnded instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextReasoningEndedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextReasoningEndedTypeEnumEnumMap = {
  SyncEventSessionNextReasoningEndedTypeEnum.sync_: 'sync',
  SyncEventSessionNextReasoningEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
