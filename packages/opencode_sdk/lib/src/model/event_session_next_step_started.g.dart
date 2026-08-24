// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_step_started.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextStepStarted _$EventSessionNextStepStartedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextStepStarted', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextStepStarted(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextStepStartedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextStepStartedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextStepStartedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextStepStartedToJson(
  EventSessionNextStepStarted instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextStepStartedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextStepStartedTypeEnumEnumMap = {
  EventSessionNextStepStartedTypeEnum.sessionPeriodNextPeriodStepPeriodStarted:
      'session.next.step.started',
  EventSessionNextStepStartedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
