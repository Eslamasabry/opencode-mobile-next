// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'subtask_part_input.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SubtaskPartInput _$SubtaskPartInputFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SubtaskPartInput', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'prompt', 'description', 'agent'],
      );
      final val = SubtaskPartInput(
        id: $checkedConvert('id', (v) => v as String?),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SubtaskPartInputTypeEnumEnumMap,
            v,
            unknownValue: SubtaskPartInputTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        prompt: $checkedConvert('prompt', (v) => v as String),
        description: $checkedConvert('description', (v) => v as String),
        agent: $checkedConvert('agent', (v) => v as String),
        model: $checkedConvert(
          'model',
          (v) => v == null
              ? null
              : SessionPromptAsyncRequestModel.fromJson(
                  v as Map<String, dynamic>,
                ),
        ),
        command: $checkedConvert('command', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$SubtaskPartInputToJson(SubtaskPartInput instance) =>
    <String, dynamic>{
      'id': ?instance.id,
      'type': _$SubtaskPartInputTypeEnumEnumMap[instance.type]!,
      'prompt': instance.prompt,
      'description': instance.description,
      'agent': instance.agent,
      'model': ?instance.model?.toJson(),
      'command': ?instance.command,
    };

const _$SubtaskPartInputTypeEnumEnumMap = {
  SubtaskPartInputTypeEnum.subtask: 'subtask',
  SubtaskPartInputTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
