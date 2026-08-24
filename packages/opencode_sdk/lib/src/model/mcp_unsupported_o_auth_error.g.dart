// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_unsupported_o_auth_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpUnsupportedOAuthError _$McpUnsupportedOAuthErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('McpUnsupportedOAuthError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['error']);
  final val = McpUnsupportedOAuthError(
    error: $checkedConvert('error', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$McpUnsupportedOAuthErrorToJson(
  McpUnsupportedOAuthError instance,
) => <String, dynamic>{'error': instance.error};
