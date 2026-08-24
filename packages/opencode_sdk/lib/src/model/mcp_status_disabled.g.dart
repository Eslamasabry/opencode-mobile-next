// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_status_disabled.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MCPStatusDisabled _$MCPStatusDisabledFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MCPStatusDisabled', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['status']);
      final val = MCPStatusDisabled(
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$MCPStatusDisabledStatusEnumEnumMap,
            v,
            unknownValue: MCPStatusDisabledStatusEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$MCPStatusDisabledToJson(MCPStatusDisabled instance) =>
    <String, dynamic>{
      'status': _$MCPStatusDisabledStatusEnumEnumMap[instance.status]!,
    };

const _$MCPStatusDisabledStatusEnumEnumMap = {
  MCPStatusDisabledStatusEnum.disabled: 'disabled',
  MCPStatusDisabledStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
