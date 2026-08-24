// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'prompt_file_attachment.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PromptFileAttachment _$PromptFileAttachmentFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PromptFileAttachment', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['uri', 'mime']);
  final val = PromptFileAttachment(
    uri: $checkedConvert('uri', (v) => v as String),
    mime: $checkedConvert('mime', (v) => v as String),
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

Map<String, dynamic> _$PromptFileAttachmentToJson(
  PromptFileAttachment instance,
) => <String, dynamic>{
  'uri': instance.uri,
  'mime': instance.mime,
  'name': ?instance.name,
  'description': ?instance.description,
  'source': ?instance.source_?.toJson(),
};
