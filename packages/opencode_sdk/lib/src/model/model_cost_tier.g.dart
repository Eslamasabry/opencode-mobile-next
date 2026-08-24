// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'model_cost_tier.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ModelCostTier _$ModelCostTierFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ModelCostTier', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['type', 'size']);
      final val = ModelCostTier(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ModelCostTierTypeEnumEnumMap,
            v,
            unknownValue: ModelCostTierTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        size: $checkedConvert('size', (v) => (v as num).toInt()),
      );
      return val;
    });

Map<String, dynamic> _$ModelCostTierToJson(ModelCostTier instance) =>
    <String, dynamic>{
      'type': _$ModelCostTierTypeEnumEnumMap[instance.type]!,
      'size': instance.size,
    };

const _$ModelCostTierTypeEnumEnumMap = {
  ModelCostTierTypeEnum.context: 'context',
  ModelCostTierTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
