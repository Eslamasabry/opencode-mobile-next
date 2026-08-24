// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_directories_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectDirectoriesInner _$ProjectDirectoriesInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProjectDirectoriesInner', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['directory']);
  final val = ProjectDirectoriesInner(
    directory: $checkedConvert('directory', (v) => v as String),
    strategy: $checkedConvert('strategy', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$ProjectDirectoriesInnerToJson(
  ProjectDirectoriesInner instance,
) => <String, dynamic>{
  'directory': instance.directory,
  'strategy': ?instance.strategy,
};
