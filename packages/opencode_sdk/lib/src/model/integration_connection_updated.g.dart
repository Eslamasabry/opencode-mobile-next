// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_connection_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationConnectionUpdated _$IntegrationConnectionUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationConnectionUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = IntegrationConnectionUpdated(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$IntegrationConnectionUpdatedTypeEnumEnumMap,
        v,
        unknownValue:
            IntegrationConnectionUpdatedTypeEnum.unknownDefaultOpenApi,
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
      (v) => v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) =>
          IntegrationConnectionUpdatedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationConnectionUpdatedToJson(
  IntegrationConnectionUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$IntegrationConnectionUpdatedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$IntegrationConnectionUpdatedTypeEnumEnumMap = {
  IntegrationConnectionUpdatedTypeEnum.integrationPeriodConnectionPeriodUpdated:
      'integration.connection.updated',
  IntegrationConnectionUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
