// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_auth_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderAuthError _$ProviderAuthErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProviderAuthError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = ProviderAuthError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$ProviderAuthErrorNameEnumEnumMap,
            v,
            unknownValue: ProviderAuthErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => ProviderAuthErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ProviderAuthErrorToJson(ProviderAuthError instance) =>
    <String, dynamic>{
      'name': _$ProviderAuthErrorNameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$ProviderAuthErrorNameEnumEnumMap = {
  ProviderAuthErrorNameEnum.providerAuthError: 'ProviderAuthError',
  ProviderAuthErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
