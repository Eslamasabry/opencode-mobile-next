// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_tool_state_completed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageToolStateCompleted _$SessionMessageToolStateCompletedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageToolStateCompleted', json, (
  $checkedConvert,
) {
  $checkKeys(
    json,
    requiredKeys: const ['status', 'input', 'content', 'structured'],
  );
  final val = SessionMessageToolStateCompleted(
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$SessionMessageToolStateCompletedStatusEnumEnumMap,
        v,
        unknownValue:
            SessionMessageToolStateCompletedStatusEnum.unknownDefaultOpenApi,
      ),
    ),
    input: $checkedConvert('input', (v) => v as Object),
    attachments: $checkedConvert(
      'attachments',
      (v) => (v as List<dynamic>?)
          ?.map((e) => PromptFileAttachment.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
    content: $checkedConvert(
      'content',
      (v) => (v as List<dynamic>).map(LLMToolContent.fromJson).toList(),
    ),
    outputPaths: $checkedConvert(
      'outputPaths',
      (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
    ),
    structured: $checkedConvert('structured', (v) => v as Object),
    result: $checkedConvert('result', (v) => v),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageToolStateCompletedToJson(
  SessionMessageToolStateCompleted instance,
) => <String, dynamic>{
  'status':
      _$SessionMessageToolStateCompletedStatusEnumEnumMap[instance.status]!,
  'input': instance.input,
  'attachments': ?instance.attachments?.map((e) => e.toJson()).toList(),
  'content': instance.content.map((e) => e.toJson()).toList(),
  'outputPaths': ?instance.outputPaths,
  'structured': instance.structured,
  'result': ?instance.result,
};

const _$SessionMessageToolStateCompletedStatusEnumEnumMap = {
  SessionMessageToolStateCompletedStatusEnum.completed: 'completed',
  SessionMessageToolStateCompletedStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
