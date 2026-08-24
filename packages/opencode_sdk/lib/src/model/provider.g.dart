// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Provider _$ProviderFromJson(Map<String, dynamic> json) => $checkedCreate(
  'Provider',
  json,
  ($checkedConvert) {
    $checkKeys(
      json,
      requiredKeys: const ['id', 'name', 'source', 'env', 'options', 'models'],
    );
    final val = Provider(
      id: $checkedConvert('id', (v) => v as String),
      name: $checkedConvert('name', (v) => v as String),
      source_: $checkedConvert(
        'source',
        (v) => $enumDecode(
          _$ProviderSource_EnumEnumMap,
          v,
          unknownValue: ProviderSource_Enum.unknownDefaultOpenApi,
        ),
      ),
      env: $checkedConvert(
        'env',
        (v) => (v as List<dynamic>).map((e) => e as String).toList(),
      ),
      key: $checkedConvert('key', (v) => v as String?),
      options: $checkedConvert('options', (v) => v as Object),
      models: $checkedConvert(
        'models',
        (v) => (v as Map<String, dynamic>).map(
          (k, e) => MapEntry(k, Model.fromJson(e as Map<String, dynamic>)),
        ),
      ),
    );
    return val;
  },
  fieldKeyMap: const {'source_': 'source'},
);

Map<String, dynamic> _$ProviderToJson(Provider instance) => <String, dynamic>{
  'id': instance.id,
  'name': instance.name,
  'source': _$ProviderSource_EnumEnumMap[instance.source_]!,
  'env': instance.env,
  'key': ?instance.key,
  'options': instance.options,
  'models': instance.models.map((k, e) => MapEntry(k, e.toJson())),
};

const _$ProviderSource_EnumEnumMap = {
  ProviderSource_Enum.env: 'env',
  ProviderSource_Enum.config: 'config',
  ProviderSource_Enum.custom: 'custom',
  ProviderSource_Enum.api: 'api',
  ProviderSource_Enum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
