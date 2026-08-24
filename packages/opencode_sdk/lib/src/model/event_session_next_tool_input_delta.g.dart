// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_tool_input_delta.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextToolInputDelta _$EventSessionNextToolInputDeltaFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextToolInputDelta', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextToolInputDelta(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextToolInputDeltaTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextToolInputDeltaTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SessionNextToolInputDeltaData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextToolInputDeltaToJson(
  EventSessionNextToolInputDelta instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextToolInputDeltaTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextToolInputDeltaTypeEnumEnumMap = {
  EventSessionNextToolInputDeltaTypeEnum
          .sessionPeriodNextPeriodToolPeriodInputPeriodDelta:
      'session.next.tool.input.delta',
  EventSessionNextToolInputDeltaTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
