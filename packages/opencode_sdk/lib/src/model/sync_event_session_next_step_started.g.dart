// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_step_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextStepStarted _$SyncEventSessionNextStepStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextStepStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextStepStarted(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextStepStartedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextStepStartedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextStepStartedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextStepStartedToJson(
  SyncEventSessionNextStepStarted instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextStepStartedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextStepStartedTypeEnumEnumMap = {
  SyncEventSessionNextStepStartedTypeEnum.sync_: 'sync',
  SyncEventSessionNextStepStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
