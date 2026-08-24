// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_skills.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigSkills _$ConfigSkillsFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ConfigSkills', json, ($checkedConvert) {
      final val = ConfigSkills(
        paths: $checkedConvert(
          'paths',
          (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
        ),
        urls: $checkedConvert(
          'urls',
          (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ConfigSkillsToJson(ConfigSkills instance) =>
    <String, dynamic>{'paths': ?instance.paths, 'urls': ?instance.urls};
