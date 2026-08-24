// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_step_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextStepEnded _$SyncEventSessionNextStepEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextStepEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextStepEnded(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextStepEndedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextStepEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextStepEndedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextStepEndedToJson(
  SyncEventSessionNextStepEnded instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextStepEndedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextStepEndedTypeEnumEnumMap = {
  SyncEventSessionNextStepEndedTypeEnum.sync_: 'sync',
  SyncEventSessionNextStepEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
