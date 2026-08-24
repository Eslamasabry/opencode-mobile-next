// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_cost.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelCost _$ModelCostFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ModelCost', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['input', 'output', 'cache']);
  final val = ModelCost(
    input: $checkedConvert('input', (v) => v as num),
    output: $checkedConvert('output', (v) => v as num),
    cache: $checkedConvert(
      'cache',
      (v) => SessionTokensCache.fromJson(v as Map<String, dynamic>),
    ),
    tiers: $checkedConvert(
      'tiers',
      (v) => (v as List<dynamic>?)
          ?.map((e) => ModelCostTiersInner.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
    experimentalOver200K: $checkedConvert(
      'experimentalOver200K',
      (v) => v == null
          ? null
          : ModelCostExperimentalOver200K.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$ModelCostToJson(ModelCost instance) => <String, dynamic>{
  'input': instance.input,
  'output': instance.output,
  'cache': instance.cache.toJson(),
  'tiers': ?instance.tiers?.map((e) => e.toJson()).toList(),
  'experimentalOver200K': ?instance.experimentalOver200K?.toJson(),
};
