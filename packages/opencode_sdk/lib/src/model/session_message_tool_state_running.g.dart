// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_tool_state_running.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageToolStateRunning _$SessionMessageToolStateRunningFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageToolStateRunning', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['status', 'input', 'structured', 'content'],
  );
  final val = SessionMessageToolStateRunning(
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$SessionMessageToolStateRunningStatusEnumEnumMap,
        v,
        unknownValue:
            SessionMessageToolStateRunningStatusEnum.unknownDefaultOpenApi,
      ),
    ),
    input: $checkedConvert('input', (v) => v as Object),
    structured: $checkedConvert('structured', (v) => v as Object),
    content: $checkedConvert(
      'content',
      (v) => (v as List<dynamic>).map(LLMToolContent.fromJson).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageToolStateRunningToJson(
  SessionMessageToolStateRunning instance,
) => <String, dynamic>{
  'status': _$SessionMessageToolStateRunningStatusEnumEnumMap[instance.status]!,
  'input': instance.input,
  'structured': instance.structured,
  'content': instance.content.map((e) => e.toJson()).toList(),
};

const _$SessionMessageToolStateRunningStatusEnumEnumMap = {
  SessionMessageToolStateRunningStatusEnum.running: 'running',
  SessionMessageToolStateRunningStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
