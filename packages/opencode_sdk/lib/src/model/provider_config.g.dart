// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_config.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderConfig _$ProviderConfigFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProviderConfig', json, ($checkedConvert) {
      final val = ProviderConfig(
        api: $checkedConvert('api', (v) => v as String?),
        name: $checkedConvert('name', (v) => v as String?),
        env: $checkedConvert(
          'env',
          (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
        ),
        id: $checkedConvert('id', (v) => v as String?),
        npm: $checkedConvert('npm', (v) => v as String?),
        whitelist: $checkedConvert(
          'whitelist',
          (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
        ),
        blacklist: $checkedConvert(
          'blacklist',
          (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
        ),
        options: $checkedConvert(
          'options',
          (v) => v == null
              ? null
              : ProviderConfigOptions.fromJson(v as Map<String, dynamic>),
        ),
        models: $checkedConvert(
          'models',
          (v) => (v as Map<String, dynamic>?)?.map(
            (k, e) => MapEntry(
              k,
              ProviderConfigModelsValue.fromJson(e as Map<String, dynamic>),
            ),
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ProviderConfigToJson(ProviderConfig instance) =>
    <String, dynamic>{
      'api': ?instance.api,
      'name': ?instance.name,
      'env': ?instance.env,
      'id': ?instance.id,
      'npm': ?instance.npm,
      'whitelist': ?instance.whitelist,
      'blacklist': ?instance.blacklist,
      'options': ?instance.options?.toJson(),
      'models': ?instance.models?.map((k, e) => MapEntry(k, e.toJson())),
    };
