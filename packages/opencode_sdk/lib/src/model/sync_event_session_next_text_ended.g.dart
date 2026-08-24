// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_text_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextTextEnded _$SyncEventSessionNextTextEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextTextEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextTextEnded(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextTextEndedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextTextEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextTextEndedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextTextEndedToJson(
  SyncEventSessionNextTextEnded instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextTextEndedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextTextEndedTypeEnumEnumMap = {
  SyncEventSessionNextTextEndedTypeEnum.sync_: 'sync',
  SyncEventSessionNextTextEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
