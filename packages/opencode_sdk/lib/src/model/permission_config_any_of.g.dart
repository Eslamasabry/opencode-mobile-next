// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_config_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionConfigAnyOf _$PermissionConfigAnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate(
  'PermissionConfigAnyOf',
  json,
  ($checkedConvert) {
    final val = PermissionConfigAnyOf(
      read: $checkedConvert(
        'read',
        (v) => v == null ? null : PermissionRuleConfig.fromJson(v),
      ),
      edit: $checkedConvert(
        'edit',
        (v) => v == null ? null : PermissionRuleConfig.fromJson(v),
      ),
      glob: $checkedConvert(
        'glob',
        (v) => v == null ? null : PermissionRuleConfig.fromJson(v),
      ),
      grep: $checkedConvert(
        'grep',
        (v) => v == null ? null : PermissionRuleConfig.fromJson(v),
      ),
      list: $checkedConvert(
        'list',
        (v) => v == null ? null : PermissionRuleConfig.fromJson(v),
      ),
      bash: $checkedConvert(
        'bash',
        (v) => v == null ? null : PermissionRuleConfig.fromJson(v),
      ),
      task: $checkedConvert(
        'task',
        (v) => v == null ? null : PermissionRuleConfig.fromJson(v),
      ),
      externalDirectory: $checkedConvert(
        'external_directory',
        (v) => v == null ? null : PermissionRuleConfig.fromJson(v),
      ),
      todowrite: $checkedConvert(
        'todowrite',
        (v) => $enumDecodeNullable(
          _$PermissionActionConfigEnumMap,
          v,
          unknownValue: PermissionActionConfig.unknownDefaultOpenApi,
        ),
      ),
      question: $checkedConvert(
        'question',
        (v) => $enumDecodeNullable(
          _$PermissionActionConfigEnumMap,
          v,
          unknownValue: PermissionActionConfig.unknownDefaultOpenApi,
        ),
      ),
      webfetch: $checkedConvert(
        'webfetch',
        (v) => $enumDecodeNullable(
          _$PermissionActionConfigEnumMap,
          v,
          unknownValue: PermissionActionConfig.unknownDefaultOpenApi,
        ),
      ),
      websearch: $checkedConvert(
        'websearch',
        (v) => $enumDecodeNullable(
          _$PermissionActionConfigEnumMap,
          v,
          unknownValue: PermissionActionConfig.unknownDefaultOpenApi,
        ),
      ),
      lsp: $checkedConvert(
        'lsp',
        (v) => v == null ? null : PermissionRuleConfig.fromJson(v),
      ),
      doomLoop: $checkedConvert(
        'doom_loop',
        (v) => $enumDecodeNullable(
          _$PermissionActionConfigEnumMap,
          v,
          unknownValue: PermissionActionConfig.unknownDefaultOpenApi,
        ),
      ),
      skill: $checkedConvert(
        'skill',
        (v) => v == null ? null : PermissionRuleConfig.fromJson(v),
      ),
    );
    return val;
  },
  fieldKeyMap: const {
    'externalDirectory': 'external_directory',
    'doomLoop': 'doom_loop',
  },
);

Map<String, dynamic> _$PermissionConfigAnyOfToJson(
  PermissionConfigAnyOf instance,
) => <String, dynamic>{
  'read': ?instance.read?.toJson(),
  'edit': ?instance.edit?.toJson(),
  'glob': ?instance.glob?.toJson(),
  'grep': ?instance.grep?.toJson(),
  'list': ?instance.list?.toJson(),
  'bash': ?instance.bash?.toJson(),
  'task': ?instance.task?.toJson(),
  'external_directory': ?instance.externalDirectory?.toJson(),
  'todowrite': ?_$PermissionActionConfigEnumMap[instance.todowrite],
  'question': ?_$PermissionActionConfigEnumMap[instance.question],
  'webfetch': ?_$PermissionActionConfigEnumMap[instance.webfetch],
  'websearch': ?_$PermissionActionConfigEnumMap[instance.websearch],
  'lsp': ?instance.lsp?.toJson(),
  'doom_loop': ?_$PermissionActionConfigEnumMap[instance.doomLoop],
  'skill': ?instance.skill?.toJson(),
};

const _$PermissionActionConfigEnumMap = {
  PermissionActionConfig.ask: 'ask',
  PermissionActionConfig.allow: 'allow',
  PermissionActionConfig.deny: 'deny',
  PermissionActionConfig.unknownDefaultOpenApi: 'unknown_default_open_api',
};
