// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'installation_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

InstallationUpdated _$InstallationUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('InstallationUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = InstallationUpdated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$InstallationUpdatedTypeEnumEnumMap,
            v,
            unknownValue: InstallationUpdatedTypeEnum.unknownDefaultOpenApi,
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
        data: $checkedConvert(
          'data',
          (v) => InstallationUpdatedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$InstallationUpdatedToJson(
  InstallationUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$InstallationUpdatedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$InstallationUpdatedTypeEnumEnumMap = {
  InstallationUpdatedTypeEnum.installationPeriodUpdated: 'installation.updated',
  InstallationUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
