// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_env_method.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationEnvMethod _$IntegrationEnvMethodFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationEnvMethod', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'names']);
  final val = IntegrationEnvMethod(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$IntegrationEnvMethodTypeEnumEnumMap,
        v,
        unknownValue: IntegrationEnvMethodTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    names: $checkedConvert(
      'names',
      (v) => (v as List<dynamic>).map((e) => e as String).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationEnvMethodToJson(
  IntegrationEnvMethod instance,
) => <String, dynamic>{
  'type': _$IntegrationEnvMethodTypeEnumEnumMap[instance.type]!,
  'names': instance.names,
};

const _$IntegrationEnvMethodTypeEnumEnumMap = {
  IntegrationEnvMethodTypeEnum.env: 'env',
  IntegrationEnvMethodTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
