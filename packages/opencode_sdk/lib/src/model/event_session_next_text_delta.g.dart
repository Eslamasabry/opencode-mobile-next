// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_text_delta.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextTextDelta _$EventSessionNextTextDeltaFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextTextDelta', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextTextDelta(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextTextDeltaTypeEnumEnumMap,
        v,
        unknownValue: EventSessionNextTextDeltaTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SessionNextTextDeltaData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextTextDeltaToJson(
  EventSessionNextTextDelta instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextTextDeltaTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextTextDeltaTypeEnumEnumMap = {
  EventSessionNextTextDeltaTypeEnum.sessionPeriodNextPeriodTextPeriodDelta:
      'session.next.text.delta',
  EventSessionNextTextDeltaTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
