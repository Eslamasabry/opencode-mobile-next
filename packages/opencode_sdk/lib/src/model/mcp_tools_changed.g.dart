// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_tools_changed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpToolsChanged _$McpToolsChangedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('McpToolsChanged', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = McpToolsChanged(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$McpToolsChangedTypeEnumEnumMap,
            v,
            unknownValue: McpToolsChangedTypeEnum.unknownDefaultOpenApi,
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
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => McpToolsChangedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$McpToolsChangedToJson(McpToolsChanged instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$McpToolsChangedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$McpToolsChangedTypeEnumEnumMap = {
  McpToolsChangedTypeEnum.mcpPeriodToolsPeriodChanged: 'mcp.tools.changed',
  McpToolsChangedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
