// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_step_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextStepFailed _$SyncEventSessionNextStepFailedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SyncEventSessionNextStepFailed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'id', 'syncEvent']);
  final val = SyncEventSessionNextStepFailed(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SyncEventSessionNextStepFailedTypeEnumEnumMap,
        v,
        unknownValue:
            SyncEventSessionNextStepFailedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    id: $checkedConvert('id', (v) => v as String),
    syncEvent: $checkedConvert(
      'syncEvent',
      (v) => SyncEventSessionNextStepFailedSyncEvent.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SyncEventSessionNextStepFailedToJson(
  SyncEventSessionNextStepFailed instance,
) => <String, dynamic>{
  'type': _$SyncEventSessionNextStepFailedTypeEnumEnumMap[instance.type]!,
  'id': instance.id,
  'syncEvent': instance.syncEvent.toJson(),
};

const _$SyncEventSessionNextStepFailedTypeEnumEnumMap = {
  SyncEventSessionNextStepFailedTypeEnum.sync_: 'sync',
  SyncEventSessionNextStepFailedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
