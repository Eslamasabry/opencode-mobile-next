// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_native.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderNative _$ProviderNativeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProviderNative', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'settings']);
      final val = ProviderNative(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ProviderNativeTypeEnumEnumMap,
            v,
            unknownValue: ProviderNativeTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        url: $checkedConvert('url', (v) => v as String?),
        settings: $checkedConvert('settings', (v) => v as Object),
      );
      return val;
    });

Map<String, dynamic> _$ProviderNativeToJson(ProviderNative instance) =>
    <String, dynamic>{
      'type': _$ProviderNativeTypeEnumEnumMap[instance.type]!,
      'url': ?instance.url,
      'settings': instance.settings,
    };

const _$ProviderNativeTypeEnumEnumMap = {
  ProviderNativeTypeEnum.native_: 'native',
  ProviderNativeTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
