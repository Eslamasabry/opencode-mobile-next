// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'agent.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Agent _$AgentFromJson(Map<String, dynamic> json) =>
    $checkedCreate('Agent', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['name', 'mode', 'permission', 'options'],
      );
      final val = Agent(
        name: $checkedConvert('name', (v) => v as String),
        description: $checkedConvert('description', (v) => v as String?),
        mode: $checkedConvert(
          'mode',
          (v) => $enumDecode(
            _$AgentModeEnumEnumMap,
            v,
            unknownValue: AgentModeEnum.unknownDefaultOpenApi,
          ),
        ),
        native_: $checkedConvert('native', (v) => v as bool?),
        hidden: $checkedConvert('hidden', (v) => v as bool?),
        topP: $checkedConvert('topP', (v) => v as num?),
        temperature: $checkedConvert('temperature', (v) => v as num?),
        color: $checkedConvert('color', (v) => v as String?),
        permission: $checkedConvert(
          'permission',
          (v) => (v as List<dynamic>)
              .map((e) => PermissionRule.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
        model: $checkedConvert(
          'model',
          (v) => v == null
              ? null
              : SessionPromptAsyncRequestModel.fromJson(
                  v as Map<String, dynamic>,
                ),
        ),
        variant: $checkedConvert('variant', (v) => v as String?),
        prompt: $checkedConvert('prompt', (v) => v as String?),
        options: $checkedConvert('options', (v) => v as Object),
        steps: $checkedConvert('steps', (v) => v as num?),
      );
      return val;
    }, fieldKeyMap: const {'native_': 'native'});

Map<String, dynamic> _$AgentToJson(Agent instance) => <String, dynamic>{
  'name': instance.name,
  'description': ?instance.description,
  'mode': _$AgentModeEnumEnumMap[instance.mode]!,
  'native': ?instance.native_,
  'hidden': ?instance.hidden,
  'topP': ?instance.topP,
  'temperature': ?instance.temperature,
  'color': ?instance.color,
  'permission': instance.permission.map((e) => e.toJson()).toList(),
  'model': ?instance.model?.toJson(),
  'variant': ?instance.variant,
  'prompt': ?instance.prompt,
  'options': instance.options,
  'steps': ?instance.steps,
};

const _$AgentModeEnumEnumMap = {
  AgentModeEnum.subagent: 'subagent',
  AgentModeEnum.primary: 'primary',
  AgentModeEnum.all: 'all',
  AgentModeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
