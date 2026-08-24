// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_integration_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventIntegrationUpdated _$EventIntegrationUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventIntegrationUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventIntegrationUpdated(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventIntegrationUpdatedTypeEnumEnumMap,
        v,
        unknownValue: EventIntegrationUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert('properties', (v) => v as Object),
  );
  return val;
});

Map<String, dynamic> _$EventIntegrationUpdatedToJson(
  EventIntegrationUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventIntegrationUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$EventIntegrationUpdatedTypeEnumEnumMap = {
  EventIntegrationUpdatedTypeEnum.integrationPeriodUpdated:
      'integration.updated',
  EventIntegrationUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
