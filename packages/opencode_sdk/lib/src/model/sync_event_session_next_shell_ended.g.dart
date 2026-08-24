// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_shell_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextShellEnded _$SyncEventSessionNextShellEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextShellEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextShellEnded(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextShellEndedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextShellEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextShellEndedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextShellEndedToJson(
  SyncEventSessionNextShellEnded instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextShellEndedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextShellEndedTypeEnumEnumMap = {
  SyncEventSessionNextShellEndedTypeEnum.sync_: 'sync',
  SyncEventSessionNextShellEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
