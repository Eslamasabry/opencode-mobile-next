// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'catalog_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CatalogUpdated _$CatalogUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('CatalogUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = CatalogUpdated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$CatalogUpdatedTypeEnumEnumMap,
            v,
            unknownValue: CatalogUpdatedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        durable: $checkedConvert(
          'durable',
          (v) => v == null
              ? null
              : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
        ),
        location: $checkedConvert(
          'location',
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert('data', (v) => v as Object),
      );
      return val;
    });

Map<String, dynamic> _$CatalogUpdatedToJson(CatalogUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$CatalogUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data,
    };

const _$CatalogUpdatedTypeEnumEnumMap = {
  CatalogUpdatedTypeEnum.catalogPeriodUpdated: 'catalog.updated',
  CatalogUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
