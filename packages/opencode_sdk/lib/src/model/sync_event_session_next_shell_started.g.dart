// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_shell_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextShellStarted _$SyncEventSessionNextShellStartedFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('SyncEventSessionNextShellStarted', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
      final val = SyncEventSessionNextShellStarted(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextShellStartedTypeEnumEnumMap,
            v,
            unknownValue:
                SyncEventSessionNextShellStartedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        syncEvent: $checkedConvert(
          'syncEvent',
          (v) => SyncEventSessionNextShellStartedSyncEvent.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextShellStartedToJson(
  SyncEventSessionNextShellStarted instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextShellStartedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextShellStartedTypeEnumEnumMap = {
  SyncEventSessionNextShellStartedTypeEnum.sync_: 'sync',
  SyncEventSessionNextShellStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
