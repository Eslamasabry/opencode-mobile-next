// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_created.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionCreated _$SyncEventSessionCreatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionCreated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionCreated(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionCreatedTypeEnumEnumMap,
        v,
        unknownValue: SyncEventSessionCreatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) =>
          SyncEventSessionCreatedSyncEvent.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionCreatedToJson(
  SyncEventSessionCreated instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionCreatedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionCreatedTypeEnumEnumMap = {
  SyncEventSessionCreatedTypeEnum.sync_: 'sync',
  SyncEventSessionCreatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
