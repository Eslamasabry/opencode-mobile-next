// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_rule.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionRule _$PermissionRuleFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PermissionRule', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['permission', 'pattern', 'action']);
      final val = PermissionRule(
        permission: $checkedConvert('permission', (v) => v as String),
        pattern: $checkedConvert('pattern', (v) => v as String),
        action: $checkedConvert(
          'action',
          (v) => $enumDecode(
            _$PermissionActionEnumMap,
            v,
            unknownValue: PermissionAction.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PermissionRuleToJson(PermissionRule instance) =>
    <String, dynamic>{
      'permission': instance.permission,
      'pattern': instance.pattern,
      'action': _$PermissionActionEnumMap[instance.action]!,
    };

const _$PermissionActionEnumMap = {
  PermissionAction.allow: 'allow',
  PermissionAction.deny: 'deny',
  PermissionAction.ask: 'ask',
  PermissionAction.unknownDefaultOpenApi: 'unknown_default_open_api',
};
