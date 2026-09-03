// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_config_models_value.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderConfigModelsValue _$ProviderConfigModelsValueFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderConfigModelsValue', json, ($checkedConvert) {
  final val = ProviderConfigModelsValue(
    id: $checkedConvert('id', (v) => v as String?),
    name: $checkedConvert('name', (v) => v as String?),
    family: $checkedConvert('family', (v) => v as String?),
    releaseDate: $checkedConvert('release_date', (v) => v as String?),
    attachment: $checkedConvert('attachment', (v) => v as bool?),
    reasoning: $checkedConvert('reasoning', (v) => v as bool?),
    temperature: $checkedConvert('temperature', (v) => v as bool?),
    toolCall: $checkedConvert('tool_call', (v) => v as bool?),
    interleaved: $checkedConvert(
      'interleaved',
      (v) => v == null ? null : OpencodeSdkRawUnion007.fromJson(v),
    ),
    cost: $checkedConvert(
      'cost',
      (v) => v == null
          ? null
          : ProviderConfigModelsValueCost.fromJson(v as Map<String, dynamic>),
    ),
    limit: $checkedConvert(
      'limit',
      (v) => v == null
          ? null
          : ProviderConfigModelsValueLimit.fromJson(v as Map<String, dynamic>),
    ),
    modalities: $checkedConvert(
      'modalities',
      (v) => v == null
          ? null
          : ProviderConfigModelsValueModalities.fromJson(
              v as Map<String, dynamic>,
            ),
    ),
    experimental: $checkedConvert('experimental', (v) => v as bool?),
    status: $checkedConvert(
      'status',
      (v) => $enumDecodeNullable(
        _$ProviderConfigModelsValueStatusEnumEnumMap,
        v,
        unknownValue: ProviderConfigModelsValueStatusEnum.unknownDefaultOpenApi,
      ),
    ),
    provider: $checkedConvert(
      'provider',
      (v) => v == null
          ? null
          : ProviderConfigModelsValueProvider.fromJson(
              v as Map<String, dynamic>,
            ),
    ),
    options: $checkedConvert('options', (v) => v),
    headers: $checkedConvert(
      'headers',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as String)),
    ),
    variants: $checkedConvert(
      'variants',
      (v) => (v as Map<String, dynamic>?)?.map(
        (k, e) => MapEntry(
          k,
          ProviderConfigModelsValueVariantsValue.fromJson(
            e as Map<String, dynamic>,
          ),
        ),
      ),
    ),
  );
  return val;
}, fieldKeyMap: const {'releaseDate': 'release_date', 'toolCall': 'tool_call'});

Map<String, dynamic> _$ProviderConfigModelsValueToJson(
  ProviderConfigModelsValue instance,
) => <String, dynamic>{
  'id': ?instance.id,
  'name': ?instance.name,
  'family': ?instance.family,
  'release_date': ?instance.releaseDate,
  'attachment': ?instance.attachment,
  'reasoning': ?instance.reasoning,
  'temperature': ?instance.temperature,
  'tool_call': ?instance.toolCall,
  'interleaved': ?instance.interleaved?.toJson(),
  'cost': ?instance.cost?.toJson(),
  'limit': ?instance.limit?.toJson(),
  'modalities': ?instance.modalities?.toJson(),
  'experimental': ?instance.experimental,
  'status': ?_$ProviderConfigModelsValueStatusEnumEnumMap[instance.status],
  'provider': ?instance.provider?.toJson(),
  'options': ?instance.options,
  'headers': ?instance.headers,
  'variants': ?instance.variants?.map((k, e) => MapEntry(k, e.toJson())),
};

const _$ProviderConfigModelsValueStatusEnumEnumMap = {
  ProviderConfigModelsValueStatusEnum.alpha: 'alpha',
  ProviderConfigModelsValueStatusEnum.beta: 'beta',
  ProviderConfigModelsValueStatusEnum.deprecated: 'deprecated',
  ProviderConfigModelsValueStatusEnum.active: 'active',
  ProviderConfigModelsValueStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
