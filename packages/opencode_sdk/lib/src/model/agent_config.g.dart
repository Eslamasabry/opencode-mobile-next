// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'agent_config.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AgentConfig _$AgentConfigFromJson(Map<String, dynamic> json) => $checkedCreate(
  'AgentConfig',
  json,
  ($checkedConvert) {
    final val = AgentConfig(
      model: $checkedConvert('model', (v) => v as String?),
      variant: $checkedConvert('variant', (v) => v as String?),
      temperature: $checkedConvert('temperature', (v) => v as num?),
      topP: $checkedConvert('top_p', (v) => v as num?),
      prompt: $checkedConvert('prompt', (v) => v as String?),
      tools: $checkedConvert(
        'tools',
        (v) =>
            (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as bool)),
      ),
      disable: $checkedConvert('disable', (v) => v as bool?),
      description: $checkedConvert('description', (v) => v as String?),
      mode: $checkedConvert(
        'mode',
        (v) => $enumDecodeNullable(
          _$AgentConfigModeEnumEnumMap,
          v,
          unknownValue: AgentConfigModeEnum.unknownDefaultOpenApi,
        ),
      ),
      hidden: $checkedConvert('hidden', (v) => v as bool?),
      options: $checkedConvert('options', (v) => v),
      color: $checkedConvert(
        'color',
        (v) => v == null ? null : OpencodeSdkRawUnion003.fromJson(v),
      ),
      steps: $checkedConvert('steps', (v) => (v as num?)?.toInt()),
      maxSteps: $checkedConvert('maxSteps', (v) => (v as num?)?.toInt()),
      permission: $checkedConvert(
        'permission',
        (v) => v == null ? null : PermissionConfig.fromJson(v),
      ),
    );
    return val;
  },
  fieldKeyMap: const {'topP': 'top_p'},
);

Map<String, dynamic> _$AgentConfigToJson(AgentConfig instance) =>
    <String, dynamic>{
      'model': ?instance.model,
      'variant': ?instance.variant,
      'temperature': ?instance.temperature,
      'top_p': ?instance.topP,
      'prompt': ?instance.prompt,
      'tools': ?instance.tools,
      'disable': ?instance.disable,
      'description': ?instance.description,
      'mode': ?_$AgentConfigModeEnumEnumMap[instance.mode],
      'hidden': ?instance.hidden,
      'options': ?instance.options,
      'color': ?instance.color?.toJson(),
      'steps': ?instance.steps,
      'maxSteps': ?instance.maxSteps,
      'permission': ?instance.permission?.toJson(),
    };

const _$AgentConfigModeEnumEnumMap = {
  AgentConfigModeEnum.subagent: 'subagent',
  AgentConfigModeEnum.primary: 'primary',
  AgentConfigModeEnum.all: 'all',
  AgentConfigModeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
