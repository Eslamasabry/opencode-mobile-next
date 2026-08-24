// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_command_value.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigCommandValue _$ConfigCommandValueFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ConfigCommandValue', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['template']);
      final val = ConfigCommandValue(
        template: $checkedConvert('template', (v) => v as String),
        description: $checkedConvert('description', (v) => v as String?),
        agent: $checkedConvert('agent', (v) => v as String?),
        model: $checkedConvert('model', (v) => v as String?),
        variant: $checkedConvert('variant', (v) => v as String?),
        subtask: $checkedConvert('subtask', (v) => v as bool?),
      );
      return val;
    });

Map<String, dynamic> _$ConfigCommandValueToJson(ConfigCommandValue instance) =>
    <String, dynamic>{
      'template': instance.template,
      'description': ?instance.description,
      'agent': ?instance.agent,
      'model': ?instance.model,
      'variant': ?instance.variant,
      'subtask': ?instance.subtask,
    };
