// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationUpdated _$IntegrationUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('IntegrationUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = IntegrationUpdated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$IntegrationUpdatedTypeEnumEnumMap,
            v,
            unknownValue: IntegrationUpdatedTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$IntegrationUpdatedToJson(IntegrationUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$IntegrationUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data,
    };

const _$IntegrationUpdatedTypeEnumEnumMap = {
  IntegrationUpdatedTypeEnum.integrationPeriodUpdated: 'integration.updated',
  IntegrationUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
