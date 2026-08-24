// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'command.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Command _$CommandFromJson(Map<String, dynamic> json) =>
    $checkedCreate('Command', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'template', 'hints']);
      final val = Command(
        name: $checkedConvert('name', (v) => v as String),
        description: $checkedConvert('description', (v) => v as String?),
        agent: $checkedConvert('agent', (v) => v as String?),
        model: $checkedConvert('model', (v) => v as String?),
        source_: $checkedConvert(
          'source',
          (v) => $enumDecodeNullable(
            _$CommandSource_EnumEnumMap,
            v,
            unknownValue: CommandSource_Enum.unknownDefaultOpenApi,
          ),
        ),
        template: $checkedConvert('template', (v) => v as String),
        subtask: $checkedConvert('subtask', (v) => v as bool?),
        hints: $checkedConvert(
          'hints',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
      );
      return val;
    }, fieldKeyMap: const {'source_': 'source'});

Map<String, dynamic> _$CommandToJson(Command instance) => <String, dynamic>{
  'name': instance.name,
  'description': ?instance.description,
  'agent': ?instance.agent,
  'model': ?instance.model,
  'source': ?_$CommandSource_EnumEnumMap[instance.source_],
  'template': instance.template,
  'subtask': ?instance.subtask,
  'hints': instance.hints,
};

const _$CommandSource_EnumEnumMap = {
  CommandSource_Enum.command: 'command',
  CommandSource_Enum.mcp: 'mcp',
  CommandSource_Enum.skill: 'skill',
  CommandSource_Enum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
