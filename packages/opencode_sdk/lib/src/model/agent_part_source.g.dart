// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'agent_part_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AgentPartSource _$AgentPartSourceFromJson(Map<String, dynamic> json) =>
    $checkedCreate('AgentPartSource', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['value', 'start', 'end']);
      final val = AgentPartSource(
        value: $checkedConvert('value', (v) => v as String),
        start: $checkedConvert('start', (v) => (v as num).toInt()),
        end: $checkedConvert('end', (v) => (v as num).toInt()),
      );
      return val;
    });

Map<String, dynamic> _$AgentPartSourceToJson(AgentPartSource instance) =>
    <String, dynamic>{
      'value': instance.value,
      'start': instance.start,
      'end': instance.end,
    };
