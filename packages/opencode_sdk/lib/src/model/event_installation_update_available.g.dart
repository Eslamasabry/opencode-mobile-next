// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_installation_update_available.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventInstallationUpdateAvailable _$EventInstallationUpdateAvailableFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('EventInstallationUpdateAvailable', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventInstallationUpdateAvailable(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventInstallationUpdateAvailableTypeEnumEnumMap,
            v,
            unknownValue:
                EventInstallationUpdateAvailableTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => InstallationUpdatedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventInstallationUpdateAvailableToJson(
  EventInstallationUpdateAvailable instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventInstallationUpdateAvailableTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventInstallationUpdateAvailableTypeEnumEnumMap = {
  EventInstallationUpdateAvailableTypeEnum.installationPeriodUpdateAvailable:
      'installation.update-available',
  EventInstallationUpdateAvailableTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
