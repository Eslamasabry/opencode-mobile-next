// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_auth_authorization.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderAuthAuthorization _$ProviderAuthAuthorizationFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderAuthAuthorization', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['url', 'method', 'instructions']);
  final val = ProviderAuthAuthorization(
    url: $checkedConvert('url', (v) => v as String),
    method: $checkedConvert(
      'method',
      (v) => $enumDecode(
        _$ProviderAuthAuthorizationMethodEnumEnumMap,
        v,
        unknownValue: ProviderAuthAuthorizationMethodEnum.unknownDefaultOpenApi,
      ),
    ),
    instructions: $checkedConvert('instructions', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$ProviderAuthAuthorizationToJson(
  ProviderAuthAuthorization instance,
) => <String, dynamic>{
  'url': instance.url,
  'method': _$ProviderAuthAuthorizationMethodEnumEnumMap[instance.method]!,
  'instructions': instance.instructions,
};

const _$ProviderAuthAuthorizationMethodEnumEnumMap = {
  ProviderAuthAuthorizationMethodEnum.auto: 'auto',
  ProviderAuthAuthorizationMethodEnum.code: 'code',
  ProviderAuthAuthorizationMethodEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
