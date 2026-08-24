// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_prompted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextPrompted _$SyncEventSessionNextPromptedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextPrompted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextPrompted(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextPromptedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextPromptedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextPromptedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextPromptedToJson(
  SyncEventSessionNextPrompted instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextPromptedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextPromptedTypeEnumEnumMap = {
  SyncEventSessionNextPromptedTypeEnum.sync_: 'sync',
  SyncEventSessionNextPromptedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
