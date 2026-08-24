// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_reasoning_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextReasoningStarted
_$SyncEventSessionNextReasoningStartedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextReasoningStarted', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
      final val = SyncEventSessionNextReasoningStarted(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextReasoningStartedTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextReasoningStartedTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        syncEvent: $checkedConvert(
          'syncEvent',
          (v) => SyncEventSessionNextReasoningStartedSyncEvent.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextReasoningStartedToJson(
  SyncEventSessionNextReasoningStarted instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextReasoningStartedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextReasoningStartedTypeEnumEnumMap = {
  SyncEventSessionNextReasoningStartedTypeEnum.sync_: 'sync',
  SyncEventSessionNextReasoningStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
