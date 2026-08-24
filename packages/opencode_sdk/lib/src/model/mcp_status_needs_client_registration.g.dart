// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_status_needs_client_registration.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

MCPStatusNeedsClientRegistration _$MCPStatusNeedsClientRegistrationFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('MCPStatusNeedsClientRegistration', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['status', 'error']);
  final val = MCPStatusNeedsClientRegistration(
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$MCPStatusNeedsClientRegistrationStatusEnumEnumMap,
        v,
        unknownValue:
            MCPStatusNeedsClientRegistrationStatusEnum.unknownDefaultOpenApi,
      ),
    ),
    error: $checkedConvert('error', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$MCPStatusNeedsClientRegistrationToJson(
  MCPStatusNeedsClientRegistration instance,
) => <String, dynamic>{
  'status':
      _$MCPStatusNeedsClientRegistrationStatusEnumEnumMap[instance.status]!,
  'error': instance.error,
};

const _$MCPStatusNeedsClientRegistrationStatusEnumEnumMap = {
  MCPStatusNeedsClientRegistrationStatusEnum.needsClientRegistration:
      'needs_client_registration',
  MCPStatusNeedsClientRegistrationStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
