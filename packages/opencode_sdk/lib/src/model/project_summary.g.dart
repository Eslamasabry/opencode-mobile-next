// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_summary.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectSummary _$ProjectSummaryFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProjectSummary', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'worktree']);
      final val = ProjectSummary(
        id: $checkedConvert('id', (v) => v as String),
        name: $checkedConvert('name', (v) => v as String?),
        worktree: $checkedConvert('worktree', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$ProjectSummaryToJson(ProjectSummary instance) =>
    <String, dynamic>{
      'id': instance.id,
      'name': ?instance.name,
      'worktree': instance.worktree,
    };
