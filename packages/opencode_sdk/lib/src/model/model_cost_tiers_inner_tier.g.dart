// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_cost_tiers_inner_tier.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelCostTiersInnerTier _$ModelCostTiersInnerTierFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ModelCostTiersInnerTier', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'size']);
  final val = ModelCostTiersInnerTier(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$ModelCostTiersInnerTierTypeEnumEnumMap,
        v,
        unknownValue: ModelCostTiersInnerTierTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    size: $checkedConvert('size', (v) => v as num),
  );
  return val;
});

Map<String, dynamic> _$ModelCostTiersInnerTierToJson(
  ModelCostTiersInnerTier instance,
) => <String, dynamic>{
  'type': _$ModelCostTiersInnerTierTypeEnumEnumMap[instance.type]!,
  'size': instance.size,
};

const _$ModelCostTiersInnerTierTypeEnumEnumMap = {
  ModelCostTiersInnerTierTypeEnum.context: 'context',
  ModelCostTiersInnerTierTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
