// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_reasoning_delta.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextReasoningDelta _$EventSessionNextReasoningDeltaFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextReasoningDelta', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextReasoningDelta(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextReasoningDeltaTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextReasoningDeltaTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SessionNextReasoningDeltaData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextReasoningDeltaToJson(
  EventSessionNextReasoningDelta instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextReasoningDeltaTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextReasoningDeltaTypeEnumEnumMap = {
  EventSessionNextReasoningDeltaTypeEnum
          .sessionPeriodNextPeriodReasoningPeriodDelta:
      'session.next.reasoning.delta',
  EventSessionNextReasoningDeltaTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
