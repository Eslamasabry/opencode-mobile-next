// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_reasoning_ended.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextReasoningEnded _$EventSessionNextReasoningEndedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextReasoningEnded', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextReasoningEnded(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextReasoningEndedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextReasoningEndedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextReasoningEndedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextReasoningEndedToJson(
  EventSessionNextReasoningEnded instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextReasoningEndedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextReasoningEndedTypeEnumEnumMap = {
  EventSessionNextReasoningEndedTypeEnum
          .sessionPeriodNextPeriodReasoningPeriodEnded:
      'session.next.reasoning.ended',
  EventSessionNextReasoningEndedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
