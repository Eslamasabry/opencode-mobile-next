// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'reference_git_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ReferenceGitSource _$ReferenceGitSourceFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ReferenceGitSource', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'repository']);
      final val = ReferenceGitSource(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ReferenceGitSourceTypeEnumEnumMap,
            v,
            unknownValue: ReferenceGitSourceTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        repository: $checkedConvert('repository', (v) => v as String),
        branch: $checkedConvert('branch', (v) => v as String?),
        description: $checkedConvert('description', (v) => v as String?),
        hidden: $checkedConvert('hidden', (v) => v as bool?),
      );
      return val;
    });

Map<String, dynamic> _$ReferenceGitSourceToJson(ReferenceGitSource instance) =>
    <String, dynamic>{
      'type': _$ReferenceGitSourceTypeEnumEnumMap[instance.type]!,
      'repository': instance.repository,
      'branch': ?instance.branch,
      'description': ?instance.description,
      'hidden': ?instance.hidden,
    };

const _$ReferenceGitSourceTypeEnumEnumMap = {
  ReferenceGitSourceTypeEnum.git: 'git',
  ReferenceGitSourceTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
