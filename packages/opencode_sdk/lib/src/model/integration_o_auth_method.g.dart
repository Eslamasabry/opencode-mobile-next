// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_o_auth_method.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationOAuthMethod _$IntegrationOAuthMethodFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationOAuthMethod', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'label']);
  final val = IntegrationOAuthMethod(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$IntegrationOAuthMethodTypeEnumEnumMap,
        v,
        unknownValue: IntegrationOAuthMethodTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    label: $checkedConvert('label', (v) => v as String),
    prompts: $checkedConvert(
      'prompts',
      (v) =>
          (v as List<dynamic>?)?.map(OpencodeSdkRawUnion023.fromJson).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationOAuthMethodToJson(
  IntegrationOAuthMethod instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$IntegrationOAuthMethodTypeEnumEnumMap[instance.type]!,
  'label': instance.label,
  'prompts': ?instance.prompts?.map((e) => e.toJson()).toList(),
};

const _$IntegrationOAuthMethodTypeEnumEnumMap = {
  IntegrationOAuthMethodTypeEnum.oauth: 'oauth',
  IntegrationOAuthMethodTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
