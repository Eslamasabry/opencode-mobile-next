// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_context_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextContextUpdated _$SyncEventSessionNextContextUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextContextUpdated', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextContextUpdated(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextContextUpdatedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextContextUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextContextUpdatedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextContextUpdatedToJson(
  SyncEventSessionNextContextUpdated instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextContextUpdatedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextContextUpdatedTypeEnumEnumMap = {
  SyncEventSessionNextContextUpdatedTypeEnum.sync_: 'sync',
  SyncEventSessionNextContextUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
