// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'worktree_ready_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorktreeReadyData _$WorktreeReadyDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorktreeReadyData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name']);
      final val = WorktreeReadyData(
        name: $checkedConvert('name', (v) => v as String),
        branch: $checkedConvert('branch', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$WorktreeReadyDataToJson(WorktreeReadyData instance) =>
    <String, dynamic>{'name': instance.name, 'branch': ?instance.branch};
