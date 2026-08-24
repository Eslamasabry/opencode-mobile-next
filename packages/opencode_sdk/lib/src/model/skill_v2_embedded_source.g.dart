// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'skill_v2_embedded_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SkillV2EmbeddedSource _$SkillV2EmbeddedSourceFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SkillV2EmbeddedSource', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'skill']);
  final val = SkillV2EmbeddedSource(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SkillV2EmbeddedSourceTypeEnumEnumMap,
        v,
        unknownValue: SkillV2EmbeddedSourceTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    skill: $checkedConvert(
      'skill',
      (v) => SkillV2Info.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$SkillV2EmbeddedSourceToJson(
  SkillV2EmbeddedSource instance,
) => <String, dynamic>{
  'type': _$SkillV2EmbeddedSourceTypeEnumEnumMap[instance.type]!,
  'skill': instance.skill.toJson(),
};

const _$SkillV2EmbeddedSourceTypeEnumEnumMap = {
  SkillV2EmbeddedSourceTypeEnum.embedded: 'embedded',
  SkillV2EmbeddedSourceTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
