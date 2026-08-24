// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_compaction_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextCompactionEnded
_$SyncEventSessionNextCompactionEndedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextCompactionEnded', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
      final val = SyncEventSessionNextCompactionEnded(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextCompactionEndedTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextCompactionEndedTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        syncEvent: $checkedConvert(
          'syncEvent',
          (v) => SyncEventSessionNextCompactionEndedSyncEvent.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextCompactionEndedToJson(
  SyncEventSessionNextCompactionEnded instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextCompactionEndedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextCompactionEndedTypeEnumEnumMap = {
  SyncEventSessionNextCompactionEndedTypeEnum.sync_: 'sync',
  SyncEventSessionNextCompactionEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
