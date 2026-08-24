// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_icon.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectIcon _$ProjectIconFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProjectIcon', json, ($checkedConvert) {
      final val = ProjectIcon(
        url: $checkedConvert('url', (v) => v as String?),
        override: $checkedConvert('override', (v) => v as String?),
        color: $checkedConvert('color', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$ProjectIconToJson(ProjectIcon instance) =>
    <String, dynamic>{
      'url': ?instance.url,
      'override': ?instance.override,
      'color': ?instance.color,
    };
