// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessageUpdated _$SyncEventMessageUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventMessageUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventMessageUpdated(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventMessageUpdatedTypeEnumEnumMap,
        v,
        unknownValue: SyncEventMessageUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) =>
          SyncEventMessageUpdatedSyncEvent.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventMessageUpdatedToJson(
  SyncEventMessageUpdated instance,
) => <String, dynamic>{
  'type': _$SyncEventMessageUpdatedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventMessageUpdatedTypeEnumEnumMap = {
  SyncEventMessageUpdatedTypeEnum.sync_: 'sync',
  SyncEventMessageUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
