// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_revert_staged.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRevertStaged _$SyncEventSessionNextRevertStagedFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('SyncEventSessionNextRevertStaged', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
      final val = SyncEventSessionNextRevertStaged(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextRevertStagedTypeEnumEnumMap,
            v,
            unknownValue:
                SyncEventSessionNextRevertStagedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        syncEvent: $checkedConvert(
          'syncEvent',
          (v) => SyncEventSessionNextRevertStagedSyncEvent.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextRevertStagedToJson(
  SyncEventSessionNextRevertStaged instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextRevertStagedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextRevertStagedTypeEnumEnumMap = {
  SyncEventSessionNextRevertStagedTypeEnum.sync_: 'sync',
  SyncEventSessionNextRevertStagedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
