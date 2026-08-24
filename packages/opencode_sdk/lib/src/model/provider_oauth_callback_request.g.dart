// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_oauth_callback_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderOauthCallbackRequest _$ProviderOauthCallbackRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderOauthCallbackRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['method']);
  final val = ProviderOauthCallbackRequest(
    method: $checkedConvert('method', (v) => v as num),
    code: $checkedConvert('code', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$ProviderOauthCallbackRequestToJson(
  ProviderOauthCallbackRequest instance,
) => <String, dynamic>{'method': instance.method, 'code': ?instance.code};
