// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'skill_v2_url_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SkillV2UrlSource _$SkillV2UrlSourceFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SkillV2UrlSource', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'url']);
      final val = SkillV2UrlSource(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SkillV2UrlSourceTypeEnumEnumMap,
            v,
            unknownValue: SkillV2UrlSourceTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        url: $checkedConvert('url', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$SkillV2UrlSourceToJson(SkillV2UrlSource instance) =>
    <String, dynamic>{
      'type': _$SkillV2UrlSourceTypeEnumEnumMap[instance.type]!,
      'url': instance.url,
    };

const _$SkillV2UrlSourceTypeEnumEnumMap = {
  SkillV2UrlSourceTypeEnum.url: 'url',
  SkillV2UrlSourceTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
