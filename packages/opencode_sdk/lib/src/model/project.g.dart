// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Project _$ProjectFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('Project', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'worktree', 'time', 'sandboxes']);
  final val = Project(
    id: $checkedConvert('id', (v) => v as String),
    worktree: $checkedConvert('worktree', (v) => v as String),
    vcs: $checkedConvert(
      'vcs',
      (v) => $enumDecodeNullable(
        _$ProjectVcsEnumMap,
        v,
        unknownValue: ProjectVcs.unknownDefaultOpenApi,
      ),
    ),
    name: $checkedConvert('name', (v) => v as String?),
    icon: $checkedConvert(
      'icon',
      (v) => v == null ? null : ProjectIcon.fromJson(v as Map<String, dynamic>),
    ),
    commands: $checkedConvert(
      'commands',
      (v) => v == null
          ? null
          : ProjectCommands.fromJson(v as Map<String, dynamic>),
    ),
    time: $checkedConvert(
      'time',
      (v) => ProjectTime.fromJson(v as Map<String, dynamic>),
    ),
    sandboxes: $checkedConvert(
      'sandboxes',
      (v) => (v as List<dynamic>).map((e) => e as String).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$ProjectToJson(Project instance) => <String, dynamic>{
  'id': instance.id,
  'worktree': instance.worktree,
  'vcs': ?_$ProjectVcsEnumMap[instance.vcs],
  'name': ?instance.name,
  'icon': ?instance.icon?.toJson(),
  'commands': ?instance.commands?.toJson(),
  'time': instance.time.toJson(),
  'sandboxes': instance.sandboxes,
};

const _$ProjectVcsEnumMap = {
  ProjectVcs.git: 'git',
  ProjectVcs.unknownDefaultOpenApi: 'unknown_default_open_api',
};
