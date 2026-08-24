// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_installation_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventInstallationUpdated _$EventInstallationUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventInstallationUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventInstallationUpdated(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventInstallationUpdatedTypeEnumEnumMap,
        v,
        unknownValue: EventInstallationUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => InstallationUpdatedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventInstallationUpdatedToJson(
  EventInstallationUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventInstallationUpdatedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventInstallationUpdatedTypeEnumEnumMap = {
  EventInstallationUpdatedTypeEnum.installationPeriodUpdated:
      'installation.updated',
  EventInstallationUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
