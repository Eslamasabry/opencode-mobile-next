// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_step_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextStepFailed _$EventSessionNextStepFailedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextStepFailed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextStepFailed(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextStepFailedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextStepFailedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextStepFailedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextStepFailedToJson(
  EventSessionNextStepFailed instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextStepFailedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextStepFailedTypeEnumEnumMap = {
  EventSessionNextStepFailedTypeEnum.sessionPeriodNextPeriodStepPeriodFailed:
      'session.next.step.failed',
  EventSessionNextStepFailedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
