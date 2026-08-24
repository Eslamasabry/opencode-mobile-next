// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_remote_config.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpRemoteConfig _$McpRemoteConfigFromJson(Map<String, dynamic> json) =>
    $checkedCreate('McpRemoteConfig', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'url']);
      final val = McpRemoteConfig(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$McpRemoteConfigTypeEnumEnumMap,
            v,
            unknownValue: McpRemoteConfigTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        url: $checkedConvert('url', (v) => v as String),
        enabled: $checkedConvert('enabled', (v) => v as bool?),
        headers: $checkedConvert(
          'headers',
          (v) => (v as Map<String, dynamic>?)?.map(
            (k, e) => MapEntry(k, e as String),
          ),
        ),
        oauth: $checkedConvert(
          'oauth',
          (v) => v == null ? null : OpencodeSdkRawUnion007.fromJson(v),
        ),
        timeout: $checkedConvert('timeout', (v) => (v as num?)?.toInt()),
      );
      return val;
    });

Map<String, dynamic> _$McpRemoteConfigToJson(McpRemoteConfig instance) =>
    <String, dynamic>{
      'type': _$McpRemoteConfigTypeEnumEnumMap[instance.type]!,
      'url': instance.url,
      'enabled': ?instance.enabled,
      'headers': ?instance.headers,
      'oauth': ?instance.oauth?.toJson(),
      'timeout': ?instance.timeout,
    };

const _$McpRemoteConfigTypeEnumEnumMap = {
  McpRemoteConfigTypeEnum.remote: 'remote',
  McpRemoteConfigTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
