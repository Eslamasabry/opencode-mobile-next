// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_mcp_browser_open_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventMcpBrowserOpenFailed _$EventMcpBrowserOpenFailedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventMcpBrowserOpenFailed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventMcpBrowserOpenFailed(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventMcpBrowserOpenFailedTypeEnumEnumMap,
        v,
        unknownValue: EventMcpBrowserOpenFailedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => McpBrowserOpenFailedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventMcpBrowserOpenFailedToJson(
  EventMcpBrowserOpenFailed instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventMcpBrowserOpenFailedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventMcpBrowserOpenFailedTypeEnumEnumMap = {
  EventMcpBrowserOpenFailedTypeEnum.mcpPeriodBrowserPeriodOpenPeriodFailed:
      'mcp.browser.open.failed',
  EventMcpBrowserOpenFailedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
