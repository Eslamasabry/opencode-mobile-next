// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_config_models_value_provider.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderConfigModelsValueProvider _$ProviderConfigModelsValueProviderFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderConfigModelsValueProvider', json, (
  $checkedConvert,
) {
  final val = ProviderConfigModelsValueProvider(
    npm: $checkedConvert('npm', (v) => v as String?),
    api: $checkedConvert('api', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$ProviderConfigModelsValueProviderToJson(
  ProviderConfigModelsValueProvider instance,
) => <String, dynamic>{'npm': ?instance.npm, 'api': ?instance.api};
