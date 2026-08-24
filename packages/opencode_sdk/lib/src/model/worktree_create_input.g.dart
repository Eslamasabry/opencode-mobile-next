// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'worktree_create_input.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorktreeCreateInput _$WorktreeCreateInputFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorktreeCreateInput', json, ($checkedConvert) {
      final val = WorktreeCreateInput(
        name: $checkedConvert('name', (v) => v as String?),
        startCommand: $checkedConvert('startCommand', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$WorktreeCreateInputToJson(
  WorktreeCreateInput instance,
) => <String, dynamic>{
  'name': ?instance.name,
  'startCommand': ?instance.startCommand,
};
