// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'prompt.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Prompt _$PromptFromJson(Map<String, dynamic> json) =>
    $checkedCreate('Prompt', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['text']);
      final val = Prompt(
        text: $checkedConvert('text', (v) => v as String),
        files: $checkedConvert(
          'files',
          (v) => (v as List<dynamic>?)
              ?.map(
                (e) => PromptFileAttachment.fromJson(e as Map<String, dynamic>),
              )
              .toList(),
        ),
        agents: $checkedConvert(
          'agents',
          (v) => (v as List<dynamic>?)
              ?.map(
                (e) =>
                    PromptAgentAttachment.fromJson(e as Map<String, dynamic>),
              )
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PromptToJson(Prompt instance) => <String, dynamic>{
  'text': instance.text,
  'files': ?instance.files?.map((e) => e.toJson()).toList(),
  'agents': ?instance.agents?.map((e) => e.toJson()).toList(),
};
