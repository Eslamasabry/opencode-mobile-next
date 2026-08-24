// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'agent_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AgentPart _$AgentPartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('AgentPart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['id', 'sessionID', 'messageID', 'type', 'name'],
      );
      final val = AgentPart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$AgentPartTypeEnumEnumMap,
            v,
            unknownValue: AgentPartTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        name: $checkedConvert('name', (v) => v as String),
        source_: $checkedConvert(
          'source',
          (v) => v == null
              ? null
              : AgentPartSource.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    }, fieldKeyMap: const {'source_': 'source'});

Map<String, dynamic> _$AgentPartToJson(AgentPart instance) => <String, dynamic>{
  'id': instance.id,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'type': _$AgentPartTypeEnumEnumMap[instance.type]!,
  'name': instance.name,
  'source': ?instance.source_?.toJson(),
};

const _$AgentPartTypeEnumEnumMap = {
  AgentPartTypeEnum.agent: 'agent',
  AgentPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
