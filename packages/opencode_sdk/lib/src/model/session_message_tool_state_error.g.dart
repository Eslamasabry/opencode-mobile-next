// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_tool_state_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageToolStateError _$SessionMessageToolStateErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageToolStateError', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['status', 'input', 'content', 'structured', 'error'],
  );
  final val = SessionMessageToolStateError(
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$SessionMessageToolStateErrorStatusEnumEnumMap,
        v,
        unknownValue:
            SessionMessageToolStateErrorStatusEnum.unknownDefaultOpenApi,
      ),
    ),
    input: $checkedConvert('input', (v) => v as Object),
    content: $checkedConvert(
      'content',
      (v) => (v as List<dynamic>).map(LLMToolContent.fromJson).toList(),
    ),
    structured: $checkedConvert('structured', (v) => v as Object),
    error: $checkedConvert(
      'error',
      (v) => SessionErrorUnknown.fromJson(v as Map<String, dynamic>),
    ),
    result: $checkedConvert('result', (v) => v),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageToolStateErrorToJson(
  SessionMessageToolStateError instance,
) => <String, dynamic>{
  'status': _$SessionMessageToolStateErrorStatusEnumEnumMap[instance.status]!,
  'input': instance.input,
  'content': instance.content.map((e) => e.toJson()).toList(),
  'structured': instance.structured,
  'error': instance.error.toJson(),
  'result': ?instance.result,
};

const _$SessionMessageToolStateErrorStatusEnumEnumMap = {
  SessionMessageToolStateErrorStatusEnum.error: 'error',
  SessionMessageToolStateErrorStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
