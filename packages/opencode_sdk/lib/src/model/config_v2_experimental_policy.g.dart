// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_v2_experimental_policy.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigV2ExperimentalPolicy _$ConfigV2ExperimentalPolicyFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ConfigV2ExperimentalPolicy', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['action', 'effect', 'resource']);
  final val = ConfigV2ExperimentalPolicy(
    action: $checkedConvert(
      'action',
      (v) => $enumDecode(
        _$ConfigV2ExperimentalPolicyActionEnumEnumMap,
        v,
        unknownValue:
            ConfigV2ExperimentalPolicyActionEnum.unknownDefaultOpenApi,
      ),
    ),
    effect: $checkedConvert(
      'effect',
      (v) => $enumDecode(
        _$PolicyEffectEnumMap,
        v,
        unknownValue: PolicyEffect.unknownDefaultOpenApi,
      ),
    ),
    resource: $checkedConvert('resource', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$ConfigV2ExperimentalPolicyToJson(
  ConfigV2ExperimentalPolicy instance,
) => <String, dynamic>{
  'action': _$ConfigV2ExperimentalPolicyActionEnumEnumMap[instance.action]!,
  'effect': _$PolicyEffectEnumMap[instance.effect]!,
  'resource': instance.resource,
};

const _$ConfigV2ExperimentalPolicyActionEnumEnumMap = {
  ConfigV2ExperimentalPolicyActionEnum.providerPeriodUse: 'provider.use',
  ConfigV2ExperimentalPolicyActionEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};

const _$PolicyEffectEnumMap = {
  PolicyEffect.allow: 'allow',
  PolicyEffect.deny: 'deny',
  PolicyEffect.unknownDefaultOpenApi: 'unknown_default_open_api',
};
