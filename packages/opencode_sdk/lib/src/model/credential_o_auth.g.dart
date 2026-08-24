// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'credential_o_auth.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CredentialOAuth _$CredentialOAuthFromJson(Map<String, dynamic> json) =>
    $checkedCreate('CredentialOAuth', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'type',
          'methodID',
          'refresh',
          'access',
          'expires',
        ],
      );
      final val = CredentialOAuth(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$CredentialOAuthTypeEnumEnumMap,
            v,
            unknownValue: CredentialOAuthTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        methodID: $checkedConvert('methodID', (v) => v as String),
        refresh: $checkedConvert('refresh', (v) => v as String),
        access: $checkedConvert('access', (v) => v as String),
        expires: $checkedConvert('expires', (v) => (v as num).toInt()),
        metadata: $checkedConvert('metadata', (v) => v),
      );
      return val;
    });

Map<String, dynamic> _$CredentialOAuthToJson(CredentialOAuth instance) =>
    <String, dynamic>{
      'type': _$CredentialOAuthTypeEnumEnumMap[instance.type]!,
      'methodID': instance.methodID,
      'refresh': instance.refresh,
      'access': instance.access,
      'expires': instance.expires,
      'metadata': ?instance.metadata,
    };

const _$CredentialOAuthTypeEnumEnumMap = {
  CredentialOAuthTypeEnum.oauth: 'oauth',
  CredentialOAuthTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
