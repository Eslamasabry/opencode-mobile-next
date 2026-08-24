// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_revert_cleared.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRevertCleared _$SyncEventSessionNextRevertClearedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextRevertCleared', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextRevertCleared(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextRevertClearedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextRevertClearedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextRevertClearedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextRevertClearedToJson(
  SyncEventSessionNextRevertCleared instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextRevertClearedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextRevertClearedTypeEnumEnumMap = {
  SyncEventSessionNextRevertClearedTypeEnum.sync_: 'sync',
  SyncEventSessionNextRevertClearedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
