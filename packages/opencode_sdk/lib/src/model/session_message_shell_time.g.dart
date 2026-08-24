// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_shell_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageShellTime _$SessionMessageShellTimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageShellTime', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['created']);
  final val = SessionMessageShellTime(
    created: $checkedConvert('created', (v) => v as num),
    completed: $checkedConvert('completed', (v) => v as num?),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageShellTimeToJson(
  SessionMessageShellTime instance,
) => <String, dynamic>{
  'created': instance.created,
  'completed': ?instance.completed,
};
