// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_cost_experimental_over200_k.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelCostExperimentalOver200K _$ModelCostExperimentalOver200KFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ModelCostExperimentalOver200K', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['input', 'output', 'cache']);
  final val = ModelCostExperimentalOver200K(
    input: $checkedConvert('input', (v) => v as num),
    output: $checkedConvert('output', (v) => v as num),
    cache: $checkedConvert(
      'cache',
      (v) => SessionTokensCache.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$ModelCostExperimentalOver200KToJson(
  ModelCostExperimentalOver200K instance,
) => <String, dynamic>{
  'input': instance.input,
  'output': instance.output,
  'cache': instance.cache.toJson(),
};
