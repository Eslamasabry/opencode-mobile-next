// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_copy_copy.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectCopyCopy _$ProjectCopyCopyFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProjectCopyCopy', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['directory']);
      final val = ProjectCopyCopy(
        directory: $checkedConvert('directory', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$ProjectCopyCopyToJson(ProjectCopyCopy instance) =>
    <String, dynamic>{'directory': instance.directory};
