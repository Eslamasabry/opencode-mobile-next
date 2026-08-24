// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'worktree.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Worktree _$WorktreeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('Worktree', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'directory']);
      final val = Worktree(
        name: $checkedConvert('name', (v) => v as String),
        branch: $checkedConvert('branch', (v) => v as String?),
        directory: $checkedConvert('directory', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$WorktreeToJson(Worktree instance) => <String, dynamic>{
  'name': instance.name,
  'branch': ?instance.branch,
  'directory': instance.directory,
};
