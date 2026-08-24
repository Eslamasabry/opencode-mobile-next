// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_agent_switched_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageAgentSwitchedTime _$SessionMessageAgentSwitchedTimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageAgentSwitchedTime', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['created']);
  final val = SessionMessageAgentSwitchedTime(
    created: $checkedConvert('created', (v) => v as num),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageAgentSwitchedTimeToJson(
  SessionMessageAgentSwitchedTime instance,
) => <String, dynamic>{'created': instance.created};
