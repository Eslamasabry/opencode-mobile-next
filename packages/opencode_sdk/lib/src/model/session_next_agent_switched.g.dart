// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_agent_switched.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextAgentSwitched _$SessionNextAgentSwitchedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextAgentSwitched', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = SessionNextAgentSwitched(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$SessionNextAgentSwitchedTypeEnumEnumMap,
        v,
        unknownValue: SessionNextAgentSwitchedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    durable: $checkedConvert(
      'durable',
      (v) => v == null
          ? null
          : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
    ),
    location: $checkedConvert(
      'location',
      (v) => v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => SyncEventSessionNextAgentSwitchedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextAgentSwitchedToJson(
  SessionNextAgentSwitched instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$SessionNextAgentSwitchedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$SessionNextAgentSwitchedTypeEnumEnumMap = {
  SessionNextAgentSwitchedTypeEnum.sessionPeriodNextPeriodAgentPeriodSwitched:
      'session.next.agent.switched',
  SessionNextAgentSwitchedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
