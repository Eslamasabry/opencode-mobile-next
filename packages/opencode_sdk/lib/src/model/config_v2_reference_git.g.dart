// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_v2_reference_git.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigV2ReferenceGit _$ConfigV2ReferenceGitFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ConfigV2ReferenceGit', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['repository']);
  final val = ConfigV2ReferenceGit(
    repository: $checkedConvert('repository', (v) => v as String),
    branch: $checkedConvert('branch', (v) => v as String?),
    description: $checkedConvert('description', (v) => v as String?),
    hidden: $checkedConvert('hidden', (v) => v as bool?),
  );
  return val;
});

Map<String, dynamic> _$ConfigV2ReferenceGitToJson(
  ConfigV2ReferenceGit instance,
) => <String, dynamic>{
  'repository': instance.repository,
  'branch': ?instance.branch,
  'description': ?instance.description,
  'hidden': ?instance.hidden,
};
