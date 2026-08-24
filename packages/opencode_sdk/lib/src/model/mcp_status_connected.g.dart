// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_status_connected.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MCPStatusConnected _$MCPStatusConnectedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MCPStatusConnected', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['status']);
      final val = MCPStatusConnected(
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$MCPStatusConnectedStatusEnumEnumMap,
            v,
            unknownValue: MCPStatusConnectedStatusEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$MCPStatusConnectedToJson(MCPStatusConnected instance) =>
    <String, dynamic>{
      'status': _$MCPStatusConnectedStatusEnumEnumMap[instance.status]!,
    };

const _$MCPStatusConnectedStatusEnumEnumMap = {
  MCPStatusConnectedStatusEnum.connected: 'connected',
  MCPStatusConnectedStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
