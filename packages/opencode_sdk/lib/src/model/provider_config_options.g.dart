// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_config_options.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderConfigOptions _$ProviderConfigOptionsFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderConfigOptions', json, ($checkedConvert) {
  final val = ProviderConfigOptions(
    apiKey: $checkedConvert('apiKey', (v) => v as String?),
    baseURL: $checkedConvert('baseURL', (v) => v as String?),
    enterpriseUrl: $checkedConvert('enterpriseUrl', (v) => v as String?),
    setCacheKey: $checkedConvert('setCacheKey', (v) => v as bool?),
    timeout: $checkedConvert(
      'timeout',
      (v) => v == null ? null : OpencodeSdkRawUnion004.fromJson(v),
    ),
    headerTimeout: $checkedConvert(
      'headerTimeout',
      (v) => v == null ? null : OpencodeSdkRawUnion005.fromJson(v),
    ),
    chunkTimeout: $checkedConvert('chunkTimeout', (v) => (v as num?)?.toInt()),
  );
  return val;
});

Map<String, dynamic> _$ProviderConfigOptionsToJson(
  ProviderConfigOptions instance,
) => <String, dynamic>{
  'apiKey': ?instance.apiKey,
  'baseURL': ?instance.baseURL,
  'enterpriseUrl': ?instance.enterpriseUrl,
  'setCacheKey': ?instance.setCacheKey,
  'timeout': ?instance.timeout?.toJson(),
  'headerTimeout': ?instance.headerTimeout?.toJson(),
  'chunkTimeout': ?instance.chunkTimeout,
};
