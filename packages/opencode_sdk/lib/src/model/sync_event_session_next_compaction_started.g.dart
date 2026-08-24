// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_compaction_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextCompactionStarted
_$SyncEventSessionNextCompactionStartedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncEventSessionNextCompactionStarted', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
      final val = SyncEventSessionNextCompactionStarted(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SyncEventSessionNextCompactionStartedTypeEnumEnumMap,
            v,
            unknownValue: SyncEventSessionNextCompactionStartedTypeEnum
                .unknownDefaultOpenApi,
          ),
        ),
        id: $checkedConvert('id', (v) => v as String),
        syncEvent: $checkedConvert(
          'syncEvent',
          (v) => SyncEventSessionNextCompactionStartedSyncEvent.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncEventSessionNextCompactionStartedToJson(
  SyncEventSessionNextCompactionStarted instance,
) => <String, dynamic>{
  'type':
      _$SyncEventSessionNextCompactionStartedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextCompactionStartedTypeEnumEnumMap = {
  SyncEventSessionNextCompactionStartedTypeEnum.sync_: 'sync',
  SyncEventSessionNextCompactionStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
