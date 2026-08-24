// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_oauth_authorize_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderOauthAuthorizeRequest _$ProviderOauthAuthorizeRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderOauthAuthorizeRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['method']);
  final val = ProviderOauthAuthorizeRequest(
    method: $checkedConvert('method', (v) => v as num),
    inputs: $checkedConvert(
      'inputs',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as String)),
    ),
  );
  return val;
});

Map<String, dynamic> _$ProviderOauthAuthorizeRequestToJson(
  ProviderOauthAuthorizeRequest instance,
) => <String, dynamic>{'method': instance.method, 'inputs': ?instance.inputs};
