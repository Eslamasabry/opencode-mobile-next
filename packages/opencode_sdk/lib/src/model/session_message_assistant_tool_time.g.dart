// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_assistant_tool_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageAssistantToolTime _$SessionMessageAssistantToolTimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageAssistantToolTime', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['created']);
  final val = SessionMessageAssistantToolTime(
    created: $checkedConvert('created', (v) => v as num),
    ran: $checkedConvert('ran', (v) => v as num?),
    completed: $checkedConvert('completed', (v) => v as num?),
    pruned: $checkedConvert('pruned', (v) => v as num?),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageAssistantToolTimeToJson(
  SessionMessageAssistantToolTime instance,
) => <String, dynamic>{
  'created': instance.created,
  'ran': ?instance.ran,
  'completed': ?instance.completed,
  'pruned': ?instance.pruned,
};
