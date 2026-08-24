// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_local_config.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpLocalConfig _$McpLocalConfigFromJson(Map<String, dynamic> json) =>
    $checkedCreate('McpLocalConfig', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'command']);
      final val = McpLocalConfig(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$McpLocalConfigTypeEnumEnumMap,
            v,
            unknownValue: McpLocalConfigTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        command: $checkedConvert(
          'command',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
        cwd: $checkedConvert('cwd', (v) => v as String?),
        environment: $checkedConvert(
          'environment',
          (v) => (v as Map<String, dynamic>?)?.map(
            (k, e) => MapEntry(k, e as String),
          ),
        ),
        enabled: $checkedConvert('enabled', (v) => v as bool?),
        timeout: $checkedConvert('timeout', (v) => (v as num?)?.toInt()),
      );
      return val;
    });

Map<String, dynamic> _$McpLocalConfigToJson(McpLocalConfig instance) =>
    <String, dynamic>{
      'type': _$McpLocalConfigTypeEnumEnumMap[instance.type]!,
      'command': instance.command,
      'cwd': ?instance.cwd,
      'environment': ?instance.environment,
      'enabled': ?instance.enabled,
      'timeout': ?instance.timeout,
    };

const _$McpLocalConfigTypeEnumEnumMap = {
  McpLocalConfigTypeEnum.local: 'local',
  McpLocalConfigTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
