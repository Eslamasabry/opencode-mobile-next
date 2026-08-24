// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_session_next_agent_switched.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventSessionNextAgentSwitched _$EventSessionNextAgentSwitchedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventSessionNextAgentSwitched', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventSessionNextAgentSwitched(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventSessionNextAgentSwitchedTypeEnumEnumMap,
        v,
        unknownValue:
            EventSessionNextAgentSwitchedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => SyncEventSessionNextAgentSwitchedSyncEventData.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventSessionNextAgentSwitchedToJson(
  EventSessionNextAgentSwitched instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventSessionNextAgentSwitchedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventSessionNextAgentSwitchedTypeEnumEnumMap = {
  EventSessionNextAgentSwitchedTypeEnum
          .sessionPeriodNextPeriodAgentPeriodSwitched:
      'session.next.agent.switched',
  EventSessionNextAgentSwitchedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
