// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_prompt_async_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionPromptAsyncRequest _$SessionPromptAsyncRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionPromptAsyncRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['parts']);
  final val = SessionPromptAsyncRequest(
    messageID: $checkedConvert('messageID', (v) => v as String?),
    model: $checkedConvert(
      'model',
      (v) => v == null
          ? null
          : SessionPromptAsyncRequestModel.fromJson(v as Map<String, dynamic>),
    ),
    agent: $checkedConvert('agent', (v) => v as String?),
    noReply: $checkedConvert('noReply', (v) => v as bool?),
    tools: $checkedConvert(
      'tools',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as bool)),
    ),
    format: $checkedConvert(
      'format',
      (v) => v == null ? null : OutputFormat.fromJson(v),
    ),
    system: $checkedConvert('system', (v) => v as String?),
    variant: $checkedConvert('variant', (v) => v as String?),
    parts: $checkedConvert(
      'parts',
      (v) => (v as List<dynamic>).map(OpencodeSdkRawUnion086.fromJson).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionPromptAsyncRequestToJson(
  SessionPromptAsyncRequest instance,
) => <String, dynamic>{
  'messageID': ?instance.messageID,
  'model': ?instance.model?.toJson(),
  'agent': ?instance.agent,
  'noReply': ?instance.noReply,
  'tools': ?instance.tools,
  'format': ?instance.format?.toJson(),
  'system': ?instance.system,
  'variant': ?instance.variant,
  'parts': instance.parts.map((e) => e.toJson()).toList(),
};
