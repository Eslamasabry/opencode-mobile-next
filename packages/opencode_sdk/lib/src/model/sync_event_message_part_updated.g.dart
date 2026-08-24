// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_part_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessagePartUpdated _$SyncEventMessagePartUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventMessagePartUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventMessagePartUpdated(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventMessagePartUpdatedTypeEnumEnumMap,
        v,
        unknownValue: SyncEventMessagePartUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventMessagePartUpdatedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventMessagePartUpdatedToJson(
  SyncEventMessagePartUpdated instance,
) => <String, dynamic>{
  'type': _$SyncEventMessagePartUpdatedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventMessagePartUpdatedTypeEnumEnumMap = {
  SyncEventMessagePartUpdatedTypeEnum.sync_: 'sync',
  SyncEventMessagePartUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
