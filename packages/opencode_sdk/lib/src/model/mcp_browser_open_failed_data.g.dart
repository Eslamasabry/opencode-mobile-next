// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_browser_open_failed_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpBrowserOpenFailedData _$McpBrowserOpenFailedDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('McpBrowserOpenFailedData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['mcpName', 'url']);
  final val = McpBrowserOpenFailedData(
    mcpName: $checkedConvert('mcpName', (v) => v as String),
    url: $checkedConvert('url', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$McpBrowserOpenFailedDataToJson(
  McpBrowserOpenFailedData instance,
) => <String, dynamic>{'mcpName': instance.mcpName, 'url': instance.url};
