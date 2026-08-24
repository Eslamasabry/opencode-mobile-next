// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'command_v2_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CommandV2Info _$CommandV2InfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('CommandV2Info', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'template']);
      final val = CommandV2Info(
        name: $checkedConvert('name', (v) => v as String),
        template: $checkedConvert('template', (v) => v as String),
        description: $checkedConvert('description', (v) => v as String?),
        agent: $checkedConvert('agent', (v) => v as String?),
        model: $checkedConvert(
          'model',
          (v) =>
              v == null ? null : ModelRef.fromJson(v as Map<String, dynamic>),
        ),
        subtask: $checkedConvert('subtask', (v) => v as bool?),
      );
      return val;
    });

Map<String, dynamic> _$CommandV2InfoToJson(CommandV2Info instance) =>
    <String, dynamic>{
      'name': instance.name,
      'template': instance.template,
      'description': ?instance.description,
      'agent': ?instance.agent,
      'model': ?instance.model?.toJson(),
      'subtask': ?instance.subtask,
    };
