// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'subtask_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SubtaskPart _$SubtaskPartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SubtaskPart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'sessionID',
          'messageID',
          'type',
          'prompt',
          'description',
          'agent',
        ],
      );
      final val = SubtaskPart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SubtaskPartTypeEnumEnumMap,
            v,
            unknownValue: SubtaskPartTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$SubtaskPartToJson(SubtaskPart instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionID': instance.sessionID,
      'messageID': instance.messageID,
      'type': _$SubtaskPartTypeEnumEnumMap[instance.type]!,
      'prompt': instance.prompt,
      'description': instance.description,
      'agent': instance.agent,
      'model': ?instance.model?.toJson(),
      'command': ?instance.command,
    };

const _$SubtaskPartTypeEnumEnumMap = {
  SubtaskPartTypeEnum.subtask: 'subtask',
  SubtaskPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
