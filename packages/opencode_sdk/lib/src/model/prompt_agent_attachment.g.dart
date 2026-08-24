// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'prompt_agent_attachment.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PromptAgentAttachment _$PromptAgentAttachmentFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PromptAgentAttachment', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['name']);
  final val = PromptAgentAttachment(
    name: $checkedConvert('name', (v) => v as String),
    source_: $checkedConvert(
      'source',
      (v) =>
          v == null ? null : PromptSource.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
}, fieldKeyMap: const {'source_': 'source'});

Map<String, dynamic> _$PromptAgentAttachmentToJson(
  PromptAgentAttachment instance,
) => <String, dynamic>{
  'name': instance.name,
  'source': ?instance.source_?.toJson(),
};
