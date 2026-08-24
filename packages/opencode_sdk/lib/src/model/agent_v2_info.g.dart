// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'agent_v2_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AgentV2Info _$AgentV2InfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('AgentV2Info', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['id', 'request', 'mode', 'hidden', 'permissions'],
      );
      final val = AgentV2Info(
        id: $checkedConvert('id', (v) => v as String),
        model: $checkedConvert(
          'model',
          (v) =>
              v == null ? null : ModelRef.fromJson(v as Map<String, dynamic>),
        ),
        request: $checkedConvert(
          'request',
          (v) => ProviderRequest.fromJson(v as Map<String, dynamic>),
        ),
        system: $checkedConvert('system', (v) => v as String?),
        description: $checkedConvert('description', (v) => v as String?),
        mode: $checkedConvert(
          'mode',
          (v) => $enumDecode(
            _$AgentV2InfoModeEnumEnumMap,
            v,
            unknownValue: AgentV2InfoModeEnum.unknownDefaultOpenApi,
          ),
        ),
        hidden: $checkedConvert('hidden', (v) => v as bool),
        color: $checkedConvert(
          'color',
          (v) => v == null ? null : AgentColor.fromJson(v),
        ),
        steps: $checkedConvert('steps', (v) => (v as num?)?.toInt()),
        permissions: $checkedConvert(
          'permissions',
          (v) => (v as List<dynamic>)
              .map((e) => PermissionV2Rule.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$AgentV2InfoToJson(AgentV2Info instance) =>
    <String, dynamic>{
      'id': instance.id,
      'model': ?instance.model?.toJson(),
      'request': instance.request.toJson(),
      'system': ?instance.system,
      'description': ?instance.description,
      'mode': _$AgentV2InfoModeEnumEnumMap[instance.mode]!,
      'hidden': instance.hidden,
      'color': ?instance.color?.toJson(),
      'steps': ?instance.steps,
      'permissions': instance.permissions.map((e) => e.toJson()).toList(),
    };

const _$AgentV2InfoModeEnumEnumMap = {
  AgentV2InfoModeEnum.subagent: 'subagent',
  AgentV2InfoModeEnum.primary: 'primary',
  AgentV2InfoModeEnum.all: 'all',
  AgentV2InfoModeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
