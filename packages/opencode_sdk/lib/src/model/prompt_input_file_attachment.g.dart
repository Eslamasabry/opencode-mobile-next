// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'prompt_input_file_attachment.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PromptInputFileAttachment _$PromptInputFileAttachmentFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PromptInputFileAttachment', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['uri']);
  final val = PromptInputFileAttachment(
    uri: $checkedConvert('uri', (v) => v as String),
    name: $checkedConvert('name', (v) => v as String?),
    description: $checkedConvert('description', (v) => v as String?),
    source_: $checkedConvert(
      'source',
      (v) =>
          v == null ? null : PromptSource.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
}, fieldKeyMap: const {'source_': 'source'});

Map<String, dynamic> _$PromptInputFileAttachmentToJson(
  PromptInputFileAttachment instance,
) => <String, dynamic>{
  'uri': instance.uri,
  'name': ?instance.name,
  'description': ?instance.description,
  'source': ?instance.source_?.toJson(),
};
