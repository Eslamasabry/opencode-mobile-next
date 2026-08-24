// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_input_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolInputStarted
_$SyncEventSessionNextToolInputStartedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextToolInputStarted', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
      final val = SyncEventSessionNextToolInputStarted(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextToolInputStartedTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextToolInputStartedTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        syncEvent: $checkedConvert(
          'syncEvent',
          (v) => SyncEventSessionNextToolInputStartedSyncEvent.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextToolInputStartedToJson(
  SyncEventSessionNextToolInputStarted instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextToolInputStartedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextToolInputStartedTypeEnumEnumMap = {
  SyncEventSessionNextToolInputStartedTypeEnum.sync_: 'sync',
  SyncEventSessionNextToolInputStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
