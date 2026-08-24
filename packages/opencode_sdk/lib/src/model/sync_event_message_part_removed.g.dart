// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_message_part_removed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventMessagePartRemoved _$SyncEventMessagePartRemovedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventMessagePartRemoved', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventMessagePartRemoved(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventMessagePartRemovedTypeEnumEnumMap,
        v,
        unknownValue: SyncEventMessagePartRemovedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventMessagePartRemovedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventMessagePartRemovedToJson(
  SyncEventMessagePartRemoved instance,
) => <String, dynamic>{
  'type': _$SyncEventMessagePartRemovedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventMessagePartRemovedTypeEnumEnumMap = {
  SyncEventMessagePartRemovedTypeEnum.sync_: 'sync',
  SyncEventMessagePartRemovedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
