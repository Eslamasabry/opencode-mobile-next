// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_revert_committed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextRevertCommitted
_$SyncEventSessionNextRevertCommittedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextRevertCommitted', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
      final val = SyncEventSessionNextRevertCommitted(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextRevertCommittedTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextRevertCommittedTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        syncEvent: $checkedConvert(
          'syncEvent',
          (v) => SyncEventSessionNextRevertCommittedSyncEvent.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextRevertCommittedToJson(
  SyncEventSessionNextRevertCommitted instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextRevertCommittedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextRevertCommittedTypeEnumEnumMap = {
  SyncEventSessionNextRevertCommittedTypeEnum.sync_: 'sync',
  SyncEventSessionNextRevertCommittedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
