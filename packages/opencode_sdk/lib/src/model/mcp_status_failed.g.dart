// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_status_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MCPStatusFailed _$MCPStatusFailedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('MCPStatusFailed', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['status', 'error']);
      final val = MCPStatusFailed(
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$MCPStatusFailedStatusEnumEnumMap,
            v,
            unknownValue: MCPStatusFailedStatusEnum.unknownDefaultOpenApi,
          ),
        ),
        error: $checkedConvert('error', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$MCPStatusFailedToJson(MCPStatusFailed instance) =>
    <String, dynamic>{
      'status': _$MCPStatusFailedStatusEnumEnumMap[instance.status]!,
      'error': instance.error,
    };

const _$MCPStatusFailedStatusEnumEnumMap = {
  MCPStatusFailedStatusEnum.failed: 'failed',
  MCPStatusFailedStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
