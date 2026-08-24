// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_config_models_value_cost.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderConfigModelsValueCost _$ProviderConfigModelsValueCostFromJson(
  Map<String, dynamic> json,
) => $checkedCreate(
  'ProviderConfigModelsValueCost',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['input', 'output']);
    final val = ProviderConfigModelsValueCost(
      input: $checkedConvert('input', (v) => v as num),
      output: $checkedConvert('output', (v) => v as num),
      cacheRead: $checkedConvert('cache_read', (v) => v as num?),
      cacheWrite: $checkedConvert('cache_write', (v) => v as num?),
      contextOver200k: $checkedConvert(
        'context_over_200k',
        (v) => v == null
            ? null
            : ProviderConfigModelsValueCostContextOver200k.fromJson(
                v as Map<String, dynamic>,
              ),
      ),
    );
    return val;
  },
  fieldKeyMap: const {
    'cacheRead': 'cache_read',
    'cacheWrite': 'cache_write',
    'contextOver200k': 'context_over_200k',
  },
);

Map<String, dynamic> _$ProviderConfigModelsValueCostToJson(
  ProviderConfigModelsValueCost instance,
) => <String, dynamic>{
  'input': instance.input,
  'output': instance.output,
  'cache_read': ?instance.cacheRead,
  'cache_write': ?instance.cacheWrite,
  'context_over_200k': ?instance.contextOver200k?.toJson(),
};
