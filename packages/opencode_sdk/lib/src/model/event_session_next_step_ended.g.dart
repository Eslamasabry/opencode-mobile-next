// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_step_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextStepEnded _$EventSessionNextStepEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextStepEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextStepEnded(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextStepEndedTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextStepEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextStepEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextStepEndedToJson(
  EventSessionNextStepEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextStepEndedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextStepEndedTypeEnumEnumMap = {
  EventSessionNextStepEndedTypeEnum.sessionPeriodNextPeriodStepPeriodEnded:
      'session.next.step.ended',
  EventSessionNextStepEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
