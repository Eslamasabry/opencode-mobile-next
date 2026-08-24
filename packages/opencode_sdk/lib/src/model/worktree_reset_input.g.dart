// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'worktree_reset_input.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorktreeResetInput _$WorktreeResetInputFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorktreeResetInput', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['directory']);
      final val = WorktreeResetInput(
        directory: $checkedConvert('directory', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$WorktreeResetInputToJson(WorktreeResetInput instance) =>
    <String, dynamic>{'directory': instance.directory};
