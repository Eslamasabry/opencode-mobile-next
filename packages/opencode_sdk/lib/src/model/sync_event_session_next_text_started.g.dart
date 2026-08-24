// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_text_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextTextStarted _$SyncEventSessionNextTextStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextTextStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextTextStarted(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextTextStartedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextTextStartedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextTextStartedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextTextStartedToJson(
  SyncEventSessionNextTextStarted instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextTextStartedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextTextStartedTypeEnumEnumMap = {
  SyncEventSessionNextTextStartedTypeEnum.sync_: 'sync',
  SyncEventSessionNextTextStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
