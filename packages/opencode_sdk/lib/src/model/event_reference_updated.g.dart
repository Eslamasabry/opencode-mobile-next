// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_reference_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventReferenceUpdated _$EventReferenceUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventReferenceUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventReferenceUpdated(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventReferenceUpdatedTypeEnumEnumMap,
        v,
        unknownValue: EventReferenceUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert('properties', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$EventReferenceUpdatedToJson(
  EventReferenceUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventReferenceUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$EventReferenceUpdatedTypeEnumEnumMap = {
  EventReferenceUpdatedTypeEnum.referencePeriodUpdated: 'reference.updated',
  EventReferenceUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
