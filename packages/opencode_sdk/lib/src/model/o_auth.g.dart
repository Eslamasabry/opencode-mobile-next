// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'o_auth.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OAuth _$OAuthFromJson(Map<String, dynamic> json) =>
    $checkedCreate('OAuth', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'refresh', 'access', 'expires'],
      );
      final val = OAuth(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$OAuthTypeEnumEnumMap,
            v,
            unknownValue: OAuthTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        refresh: $checkedConvert('refresh', (v) => v as String),
        access: $checkedConvert('access', (v) => v as String),
        expires: $checkedConvert('expires', (v) => (v as num).toInt()),
        accountId: $checkedConvert('accountId', (v) => v as String?),
        enterpriseUrl: $checkedConvert('enterpriseUrl', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$OAuthToJson(OAuth instance) => <String, dynamic>{
  'type': _$OAuthTypeEnumEnumMap[instance.type]!,
  'refresh': instance.refresh,
  'access': instance.access,
  'expires': instance.expires,
  'accountId': ?instance.accountId,
  'enterpriseUrl': ?instance.enterpriseUrl,
};

const _$OAuthTypeEnumEnumMap = {
  OAuthTypeEnum.oauth: 'oauth',
  OAuthTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
