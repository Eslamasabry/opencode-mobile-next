// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'skill_v2_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SkillV2Info _$SkillV2InfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SkillV2Info', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'location', 'content']);
      final val = SkillV2Info(
        name: $checkedConvert('name', (v) => v as String),
        description: $checkedConvert('description', (v) => v as String?),
        slash: $checkedConvert('slash', (v) => v as bool?),
        location: $checkedConvert('location', (v) => v as String),
        content: $checkedConvert('content', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$SkillV2InfoToJson(SkillV2Info instance) =>
    <String, dynamic>{
      'name': instance.name,
      'description': ?instance.description,
      'slash': ?instance.slash,
      'location': instance.location,
      'content': instance.content,
    };
