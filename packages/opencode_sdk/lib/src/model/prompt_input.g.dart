// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'prompt_input.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PromptInput _$PromptInputFromJson(Map<String, dynamic> json) => $checkedCreate(
  'PromptInput',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['text']);
    final val = PromptInput(
      text: $checkedConvert('text', (v) => v as String),
      files: $checkedConvert(
        'files',
        (v) => (v as List<dynamic>?)
            ?.map(
              (e) =>
                  PromptInputFileAttachment.fromJson(e as Map<String, dynamic>),
            )
            .toList(),
      ),
      agents: $checkedConvert(
        'agents',
        (v) => (v as List<dynamic>?)
            ?.map(
              (e) => PromptAgentAttachment.fromJson(e as Map<String, dynamic>),
            )
            .toList(),
      ),
    );
    return val;
  },
);

Map<String, dynamic> _$PromptInputToJson(PromptInput instance) =>
    <String, dynamic>{
      'text': instance.text,
      'files': ?instance.files?.map((e) => e.toJson()).toList(),
      'agents': ?instance.agents?.map((e) => e.toJson()).toList(),
    };
