// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_catalog_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventCatalogUpdated _$EventCatalogUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventCatalogUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventCatalogUpdated(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventCatalogUpdatedTypeEnumEnumMap,
            v,
            unknownValue: EventCatalogUpdatedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert('properties', (v) => v as Object),
      );
      return val;
    });

Map<String, dynamic> _$EventCatalogUpdatedToJson(
  EventCatalogUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventCatalogUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties,
};

const _$EventCatalogUpdatedTypeEnumEnumMap = {
  EventCatalogUpdatedTypeEnum.catalogPeriodUpdated: 'catalog.updated',
  EventCatalogUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
