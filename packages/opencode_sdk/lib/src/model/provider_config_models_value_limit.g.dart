// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_config_models_value_limit.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderConfigModelsValueLimit _$ProviderConfigModelsValueLimitFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProviderConfigModelsValueLimit', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['context', 'output']);
  final val = ProviderConfigModelsValueLimit(
    context: $checkedConvert('context', (v) => v as num),
    input: $checkedConvert('input', (v) => v as num?),
    output: $checkedConvert('output', (v) => v as num),
  );
  return val;
});

Map<String, dynamic> _$ProviderConfigModelsValueLimitToJson(
  ProviderConfigModelsValueLimit instance,
) => <String, dynamic>{
  'context': instance.context,
  'input': ?instance.input,
  'output': instance.output,
};
