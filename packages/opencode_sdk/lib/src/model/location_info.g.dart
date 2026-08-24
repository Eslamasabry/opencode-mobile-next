// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'location_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

LocationInfo _$LocationInfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('LocationInfo', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['directory', 'project']);
      final val = LocationInfo(
        directory: $checkedConvert('directory', (v) => v as String),
        workspaceID: $checkedConvert('workspaceID', (v) => v as String?),
        project: $checkedConvert(
          'project',
          (v) => LocationInfoProject.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$LocationInfoToJson(LocationInfo instance) =>
    <String, dynamic>{
      'directory': instance.directory,
      'workspaceID': ?instance.workspaceID,
      'project': instance.project.toJson(),
    };
