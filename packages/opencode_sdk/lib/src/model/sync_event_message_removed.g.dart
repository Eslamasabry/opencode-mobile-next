// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_removed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessageRemoved _$SyncEventMessageRemovedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventMessageRemoved', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventMessageRemoved(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventMessageRemovedTypeEnumEnumMap,
        v,
        unknownValue: SyncEventMessageRemovedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) =>
          SyncEventMessageRemovedSyncEvent.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventMessageRemovedToJson(
  SyncEventMessageRemoved instance,
) => <String, dynamic>{
  'type': _$SyncEventMessageRemovedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventMessageRemovedTypeEnumEnumMap = {
  SyncEventMessageRemovedTypeEnum.sync_: 'sync',
  SyncEventMessageRemovedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
