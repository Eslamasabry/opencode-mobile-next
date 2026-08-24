// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_synthetic.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextSynthetic _$SyncEventSessionNextSyntheticFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextSynthetic', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextSynthetic(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextSyntheticTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextSyntheticTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextSyntheticSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextSyntheticToJson(
  SyncEventSessionNextSynthetic instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextSyntheticTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextSyntheticTypeEnumEnumMap = {
  SyncEventSessionNextSyntheticTypeEnum.sync_: 'sync',
  SyncEventSessionNextSyntheticTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
