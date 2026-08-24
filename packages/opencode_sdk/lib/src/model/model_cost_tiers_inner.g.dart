// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_cost_tiers_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelCostTiersInner _$ModelCostTiersInnerFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelCostTiersInner', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['input', 'output', 'cache', 'tier'],
      );
      final val = ModelCostTiersInner(
        input: $checkedConvert('input', (v) => v as num),
        output: $checkedConvert('output', (v) => v as num),
        cache: $checkedConvert(
          'cache',
          (v) => SessionTokensCache.fromJson(v as Map<String, dynamic>),
        ),
        tier: $checkedConvert(
          'tier',
          (v) => ModelCostTiersInnerTier.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ModelCostTiersInnerToJson(
  ModelCostTiersInner instance,
) => <String, dynamic>{
  'input': instance.input,
  'output': instance.output,
  'cache': instance.cache.toJson(),
  'tier': instance.tier.toJson(),
};
