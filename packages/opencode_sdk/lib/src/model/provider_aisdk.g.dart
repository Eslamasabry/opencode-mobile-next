// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_aisdk.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderAISDK _$ProviderAISDKFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProviderAISDK', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'package']);
      final val = ProviderAISDK(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ProviderAISDKTypeEnumEnumMap,
            v,
            unknownValue: ProviderAISDKTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        package: $checkedConvert('package', (v) => v as String),
        url: $checkedConvert('url', (v) => v as String?),
        settings: $checkedConvert('settings', (v) => v),
      );
      return val;
    });

Map<String, dynamic> _$ProviderAISDKToJson(ProviderAISDK instance) =>
    <String, dynamic>{
      'type': _$ProviderAISDKTypeEnumEnumMap[instance.type]!,
      'package': instance.package,
      'url': ?instance.url,
      'settings': ?instance.settings,
    };

const _$ProviderAISDKTypeEnumEnumMap = {
  ProviderAISDKTypeEnum.aisdk: 'aisdk',
  ProviderAISDKTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
