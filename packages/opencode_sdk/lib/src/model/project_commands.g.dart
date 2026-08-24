// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_commands.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectCommands _$ProjectCommandsFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProjectCommands', json, ($checkedConvert) {
      final val = ProjectCommands(
        start: $checkedConvert('start', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$ProjectCommandsToJson(ProjectCommands instance) =>
    <String, dynamic>{'start': ?instance.start};
