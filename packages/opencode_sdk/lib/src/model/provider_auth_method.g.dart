// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_auth_method.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderAuthMethod _$ProviderAuthMethodFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProviderAuthMethod', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'label']);
      final val = ProviderAuthMethod(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ProviderAuthMethodTypeEnumEnumMap,
            v,
            unknownValue: ProviderAuthMethodTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        label: $checkedConvert('label', (v) => v as String),
        prompts: $checkedConvert(
          'prompts',
          (v) => (v as List<dynamic>?)
              ?.map(OpencodeSdkRawUnion016.fromJson)
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ProviderAuthMethodToJson(ProviderAuthMethod instance) =>
    <String, dynamic>{
      'type': _$ProviderAuthMethodTypeEnumEnumMap[instance.type]!,
      'label': instance.label,
      'prompts': ?instance.prompts?.map((e) => e.toJson()).toList(),
    };

const _$ProviderAuthMethodTypeEnumEnumMap = {
  ProviderAuthMethodTypeEnum.oauth: 'oauth',
  ProviderAuthMethodTypeEnum.api: 'api',
  ProviderAuthMethodTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
