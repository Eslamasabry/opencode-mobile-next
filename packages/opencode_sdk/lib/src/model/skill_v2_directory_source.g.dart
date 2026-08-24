// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'skill_v2_directory_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SkillV2DirectorySource _$SkillV2DirectorySourceFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SkillV2DirectorySource', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'path']);
  final val = SkillV2DirectorySource(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SkillV2DirectorySourceTypeEnumEnumMap,
        v,
        unknownValue: SkillV2DirectorySourceTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    path: $checkedConvert('path', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SkillV2DirectorySourceToJson(
  SkillV2DirectorySource instance,
) => <String, dynamic>{
  'type': _$SkillV2DirectorySourceTypeEnumEnumMap[instance.type]!,
  'path': instance.path,
};

const _$SkillV2DirectorySourceTypeEnumEnumMap = {
  SkillV2DirectorySourceTypeEnum.directory: 'directory',
  SkillV2DirectorySourceTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
