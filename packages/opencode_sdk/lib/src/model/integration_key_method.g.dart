// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_key_method.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationKeyMethod _$IntegrationKeyMethodFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationKeyMethod', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type']);
  final val = IntegrationKeyMethod(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$IntegrationKeyMethodTypeEnumEnumMap,
        v,
        unknownValue: IntegrationKeyMethodTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    label: $checkedConvert('label', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$IntegrationKeyMethodToJson(
  IntegrationKeyMethod instance,
) => <String, dynamic>{
  'type': _$IntegrationKeyMethodTypeEnumEnumMap[instance.type]!,
  'label': ?instance.label,
};

const _$IntegrationKeyMethodTypeEnumEnumMap = {
  IntegrationKeyMethodTypeEnum.key: 'key',
  IntegrationKeyMethodTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
