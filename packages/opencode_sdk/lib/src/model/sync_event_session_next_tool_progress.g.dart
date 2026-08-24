// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_progress.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolProgress _$SyncEventSessionNextToolProgressFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('SyncEventSessionNextToolProgress', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
      final val = SyncEventSessionNextToolProgress(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextToolProgressTypeEnumEnumMap,
            v,
            unknownValue:
                SyncEventSessionNextToolProgressTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        syncEvent: $checkedConvert(
          'syncEvent',
          (v) => SyncEventSessionNextToolProgressSyncEvent.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextToolProgressToJson(
  SyncEventSessionNextToolProgress instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextToolProgressTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextToolProgressTypeEnumEnumMap = {
  SyncEventSessionNextToolProgressTypeEnum.sync_: 'sync',
  SyncEventSessionNextToolProgressTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
