// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_switch_agent_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionSwitchAgentRequest _$V2SessionSwitchAgentRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionSwitchAgentRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['agent']);
  final val = V2SessionSwitchAgentRequest(
    agent: $checkedConvert('agent', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$V2SessionSwitchAgentRequestToJson(
  V2SessionSwitchAgentRequest instance,
) => <String, dynamic>{'agent': instance.agent};
