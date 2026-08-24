// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'provider_config_models_value_variants_value.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProviderConfigModelsValueVariantsValue
_$ProviderConfigModelsValueVariantsValueFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProviderConfigModelsValueVariantsValue', json, (
      $checkedConvert,
    ) {
      final val = ProviderConfigModelsValueVariantsValue(
        disabled: $checkedConvert('disabled', (v) => v as bool?),
      );
      return val;
    });

Map<String, dynamic> _$ProviderConfigModelsValueVariantsValueToJson(
  ProviderConfigModelsValueVariantsValue instance,
) => <String, dynamic>{'disabled': ?instance.disabled};
