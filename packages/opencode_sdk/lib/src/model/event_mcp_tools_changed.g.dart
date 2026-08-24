// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_mcp_tools_changed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventMcpToolsChanged _$EventMcpToolsChangedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventMcpToolsChanged', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventMcpToolsChanged(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventMcpToolsChangedTypeEnumEnumMap,
        v,
        unknownValue: EventMcpToolsChangedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => McpToolsChangedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventMcpToolsChangedToJson(
  EventMcpToolsChanged instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventMcpToolsChangedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventMcpToolsChangedTypeEnumEnumMap = {
  EventMcpToolsChangedTypeEnum.mcpPeriodToolsPeriodChanged: 'mcp.tools.changed',
  EventMcpToolsChangedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
