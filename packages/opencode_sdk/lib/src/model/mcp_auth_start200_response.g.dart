// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_auth_start200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpAuthStart200Response _$McpAuthStart200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('McpAuthStart200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['authorizationUrl', 'oauthState']);
  final val = McpAuthStart200Response(
    authorizationUrl: $checkedConvert('authorizationUrl', (v) => v as String),
    oauthState: $checkedConvert('oauthState', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$McpAuthStart200ResponseToJson(
  McpAuthStart200Response instance,
) => <String, dynamic>{
  'authorizationUrl': instance.authorizationUrl,
  'oauthState': instance.oauthState,
};
