// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_message_part_delta.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventMessagePartDelta _$EventMessagePartDeltaFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventMessagePartDelta', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventMessagePartDelta(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventMessagePartDeltaTypeEnumEnumMap,
        v,
        unknownValue: EventMessagePartDeltaTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => MessagePartDeltaData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventMessagePartDeltaToJson(
  EventMessagePartDelta instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventMessagePartDeltaTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventMessagePartDeltaTypeEnumEnumMap = {
  EventMessagePartDeltaTypeEnum.messagePeriodPartPeriodDelta:
      'message.part.delta',
  EventMessagePartDeltaTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
