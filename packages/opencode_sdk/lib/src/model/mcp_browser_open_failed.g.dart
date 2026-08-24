// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_browser_open_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpBrowserOpenFailed _$McpBrowserOpenFailedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('McpBrowserOpenFailed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = McpBrowserOpenFailed(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$McpBrowserOpenFailedTypeEnumEnumMap,
        v,
        unknownValue: McpBrowserOpenFailedTypeEnum.unknownDefaultOpenApi,
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
      (v) => McpBrowserOpenFailedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$McpBrowserOpenFailedToJson(
  McpBrowserOpenFailed instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$McpBrowserOpenFailedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$McpBrowserOpenFailedTypeEnumEnumMap = {
  McpBrowserOpenFailedTypeEnum.mcpPeriodBrowserPeriodOpenPeriodFailed:
      'mcp.browser.open.failed',
  McpBrowserOpenFailedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
