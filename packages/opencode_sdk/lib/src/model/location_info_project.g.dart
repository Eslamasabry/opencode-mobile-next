// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'location_info_project.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

LocationInfoProject _$LocationInfoProjectFromJson(Map<String, dynamic> json) =>
    $checkedCreate('LocationInfoProject', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'directory']);
      final val = LocationInfoProject(
        id: $checkedConvert('id', (v) => v as String),
        directory: $checkedConvert('directory', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$LocationInfoProjectToJson(
  LocationInfoProject instance,
) => <String, dynamic>{'id': instance.id, 'directory': instance.directory};
