// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_v2_rule.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionV2Rule _$PermissionV2RuleFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PermissionV2Rule', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['action', 'resource', 'effect']);
      final val = PermissionV2Rule(
        action: $checkedConvert('action', (v) => v as String),
        resource: $checkedConvert('resource', (v) => v as String),
        effect: $checkedConvert(
          'effect',
          (v) => $enumDecode(
            _$PermissionV2EffectEnumMap,
            v,
            unknownValue: PermissionV2Effect.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PermissionV2RuleToJson(PermissionV2Rule instance) =>
    <String, dynamic>{
      'action': instance.action,
      'resource': instance.resource,
      'effect': _$PermissionV2EffectEnumMap[instance.effect]!,
    };

const _$PermissionV2EffectEnumMap = {
  PermissionV2Effect.allow: 'allow',
  PermissionV2Effect.deny: 'deny',
  PermissionV2Effect.ask: 'ask',
  PermissionV2Effect.unknownDefaultOpenApi: 'unknown_default_open_api',
};
