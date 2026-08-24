// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_auth_error1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderAuthError1 _$ProviderAuthError1FromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProviderAuthError1', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = ProviderAuthError1(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$ProviderAuthError1NameEnumEnumMap,
            v,
            unknownValue: ProviderAuthError1NameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => ProviderAuthError1Data.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ProviderAuthError1ToJson(ProviderAuthError1 instance) =>
    <String, dynamic>{
      'name': _$ProviderAuthError1NameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$ProviderAuthError1NameEnumEnumMap = {
  ProviderAuthError1NameEnum.badRequest: 'BadRequest',
  ProviderAuthError1NameEnum.providerAuthOauthMissing:
      'ProviderAuthOauthMissing',
  ProviderAuthError1NameEnum.providerAuthOauthCodeMissing:
      'ProviderAuthOauthCodeMissing',
  ProviderAuthError1NameEnum.providerAuthOauthCallbackFailed:
      'ProviderAuthOauthCallbackFailed',
  ProviderAuthError1NameEnum.providerAuthValidationFailed:
      'ProviderAuthValidationFailed',
  ProviderAuthError1NameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
