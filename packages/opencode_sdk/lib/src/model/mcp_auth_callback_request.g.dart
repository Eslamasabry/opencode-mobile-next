// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_auth_callback_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpAuthCallbackRequest _$McpAuthCallbackRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('McpAuthCallbackRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['code']);
  final val = McpAuthCallbackRequest(
    code: $checkedConvert('code', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$McpAuthCallbackRequestToJson(
  McpAuthCallbackRequest instance,
) => <String, dynamic>{'code': instance.code};
