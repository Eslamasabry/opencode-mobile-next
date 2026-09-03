// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_config_models_value_cost_context_over200k.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderConfigModelsValueCostContextOver200k
_$ProviderConfigModelsValueCostContextOver200kFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderConfigModelsValueCostContextOver200k', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['input', 'output']);
  final val = ProviderConfigModelsValueCostContextOver200k(
    input: $checkedConvert('input', (v) => v as num),
    output: $checkedConvert('output', (v) => v as num),
    cacheRead: $checkedConvert('cache_read', (v) => v as num?),
    cacheWrite: $checkedConvert('cache_write', (v) => v as num?),
  );
  return val;
}, fieldKeyMap: const {'cacheRead': 'cache_read', 'cacheWrite': 'cache_write'});

Map<String, dynamic> _$ProviderConfigModelsValueCostContextOver200kToJson(
  ProviderConfigModelsValueCostContextOver200k instance,
) => <String, dynamic>{
  'input': instance.input,
  'output': instance.output,
  'cache_read': ?instance.cacheRead,
  'cache_write': ?instance.cacheWrite,
};
