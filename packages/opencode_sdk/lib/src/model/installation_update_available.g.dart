// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'installation_update_available.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

InstallationUpdateAvailable _$InstallationUpdateAvailableFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('InstallationUpdateAvailable', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = InstallationUpdateAvailable(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$InstallationUpdateAvailableTypeEnumEnumMap,
        v,
        unknownValue: InstallationUpdateAvailableTypeEnum.unknownDefaultOpenApi,
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
      (v) => InstallationUpdatedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$InstallationUpdateAvailableToJson(
  InstallationUpdateAvailable instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$InstallationUpdateAvailableTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$InstallationUpdateAvailableTypeEnumEnumMap = {
  InstallationUpdateAvailableTypeEnum.installationPeriodUpdateAvailable:
      'installation.update-available',
  InstallationUpdateAvailableTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
