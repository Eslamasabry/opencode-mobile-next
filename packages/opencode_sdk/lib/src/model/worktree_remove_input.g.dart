// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'worktree_remove_input.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorktreeRemoveInput _$WorktreeRemoveInputFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorktreeRemoveInput', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['directory']);
      final val = WorktreeRemoveInput(
        directory: $checkedConvert('directory', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$WorktreeRemoveInputToJson(
  WorktreeRemoveInput instance,
) => <String, dynamic>{'directory': instance.directory};
