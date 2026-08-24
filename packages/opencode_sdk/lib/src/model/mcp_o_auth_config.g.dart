// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'mcp_o_auth_config.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

McpOAuthConfig _$McpOAuthConfigFromJson(Map<String, dynamic> json) =>
    $checkedCreate('McpOAuthConfig', json, ($checkedConvert) {
      final val = McpOAuthConfig(
        clientId: $checkedConvert('clientId', (v) => v as String?),
        clientSecret: $checkedConvert('clientSecret', (v) => v as String?),
        scope: $checkedConvert('scope', (v) => v as String?),
        callbackPort: $checkedConvert(
          'callbackPort',
          (v) => (v as num?)?.toInt(),
        ),
        redirectUri: $checkedConvert('redirectUri', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$McpOAuthConfigToJson(McpOAuthConfig instance) =>
    <String, dynamic>{
      'clientId': ?instance.clientId,
      'clientSecret': ?instance.clientSecret,
      'scope': ?instance.scope,
      'callbackPort': ?instance.callbackPort,
      'redirectUri': ?instance.redirectUri,
    };
