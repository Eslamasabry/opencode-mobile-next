// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_server_instance_disposed_properties.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventServerInstanceDisposedProperties
_$EventServerInstanceDisposedPropertiesFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventServerInstanceDisposedProperties', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['directory']);
      final val = EventServerInstanceDisposedProperties(
        directory: $checkedConvert('directory', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$EventServerInstanceDisposedPropertiesToJson(
  EventServerInstanceDisposedProperties instance,
) => <String, dynamic>{'directory': instance.directory};
