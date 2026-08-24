// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'assistant_message_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AssistantMessageTime _$AssistantMessageTimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('AssistantMessageTime', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['created']);
  final val = AssistantMessageTime(
    created: $checkedConvert('created', (v) => (v as num).toInt()),
    completed: $checkedConvert('completed', (v) => (v as num?)?.toInt()),
  );
  return val;
});

Map<String, dynamic> _$AssistantMessageTimeToJson(
  AssistantMessageTime instance,
) => <String, dynamic>{
  'created': instance.created,
  'completed': ?instance.completed,
};
