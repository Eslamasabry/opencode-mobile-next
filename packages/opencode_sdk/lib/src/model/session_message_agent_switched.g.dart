// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_agent_switched.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageAgentSwitched _$SessionMessageAgentSwitchedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessageAgentSwitched', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'time', 'type', 'agent']);
  final val = SessionMessageAgentSwitched(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    time: $checkedConvert(
      'time',
      (v) =>
          SessionMessageAgentSwitchedTime.fromJson(v as Map<String, dynamic>),
    ),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionMessageAgentSwitchedTypeEnumEnumMap,
        v,
        unknownValue: SessionMessageAgentSwitchedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    agent: $checkedConvert('agent', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$SessionMessageAgentSwitchedToJson(
  SessionMessageAgentSwitched instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'time': instance.time.toJson(),
  'type': _$SessionMessageAgentSwitchedTypeEnumEnumMap[instance.type]!,
  'agent': instance.agent,
};

const _$SessionMessageAgentSwitchedTypeEnumEnumMap = {
  SessionMessageAgentSwitchedTypeEnum.agentSwitched: 'agent-switched',
  SessionMessageAgentSwitchedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
