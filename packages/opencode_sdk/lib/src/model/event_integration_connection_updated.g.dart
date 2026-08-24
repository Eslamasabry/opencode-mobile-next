// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_integration_connection_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventIntegrationConnectionUpdated _$EventIntegrationConnectionUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventIntegrationConnectionUpdated', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventIntegrationConnectionUpdated(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventIntegrationConnectionUpdatedTypeEnumEnumMap,
        v,
        unknownValue:
            EventIntegrationConnectionUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) =>
          IntegrationConnectionUpdatedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventIntegrationConnectionUpdatedToJson(
  EventIntegrationConnectionUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventIntegrationConnectionUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventIntegrationConnectionUpdatedTypeEnumEnumMap = {
  EventIntegrationConnectionUpdatedTypeEnum
          .integrationPeriodConnectionPeriodUpdated:
      'integration.connection.updated',
  EventIntegrationConnectionUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
