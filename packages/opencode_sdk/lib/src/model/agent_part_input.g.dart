// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'agent_part_input.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AgentPartInput _$AgentPartInputFromJson(Map<String, dynamic> json) =>
    $checkedCreate('AgentPartInput', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'name']);
      final val = AgentPartInput(
        id: $checkedConvert('id', (v) => v as String?),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$AgentPartInputTypeEnumEnumMap,
            v,
            unknownValue: AgentPartInputTypeEnum.unknownDefaultOpenApi,
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

Map<String, dynamic> _$AgentPartInputToJson(AgentPartInput instance) =>
    <String, dynamic>{
      'id': ?instance.id,
      'type': _$AgentPartInputTypeEnumEnumMap[instance.type]!,
      'name': instance.name,
      'source': ?instance.source_?.toJson(),
    };

const _$AgentPartInputTypeEnumEnumMap = {
  AgentPartInputTypeEnum.agent: 'agent',
  AgentPartInputTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
