// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_status_needs_auth.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MCPStatusNeedsAuth _$MCPStatusNeedsAuthFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MCPStatusNeedsAuth', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['status']);
      final val = MCPStatusNeedsAuth(
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$MCPStatusNeedsAuthStatusEnumEnumMap,
            v,
            unknownValue: MCPStatusNeedsAuthStatusEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$MCPStatusNeedsAuthToJson(MCPStatusNeedsAuth instance) =>
    <String, dynamic>{
      'status': _$MCPStatusNeedsAuthStatusEnumEnumMap[instance.status]!,
    };

const _$MCPStatusNeedsAuthStatusEnumEnumMap = {
  MCPStatusNeedsAuthStatusEnum.needsAuth: 'needs_auth',
  MCPStatusNeedsAuthStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
