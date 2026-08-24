// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_tools_changed_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpToolsChangedData _$McpToolsChangedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('McpToolsChangedData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['server']);
      final val = McpToolsChangedData(
        server: $checkedConvert('server', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$McpToolsChangedDataToJson(
  McpToolsChangedData instance,
) => <String, dynamic>{'server': instance.server};
