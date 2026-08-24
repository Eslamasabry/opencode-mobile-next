// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_when.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationWhen _$IntegrationWhenFromJson(Map<String, dynamic> json) =>
    $checkedCreate('IntegrationWhen', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['key', 'op', 'value']);
      final val = IntegrationWhen(
        key: $checkedConvert('key', (v) => v as String),
        op: $checkedConvert(
          'op',
          (v) => $enumDecode(
            _$IntegrationWhenOpEnumEnumMap,
            v,
            unknownValue: IntegrationWhenOpEnum.unknownDefaultOpenApi,
          ),
        ),
        value: $checkedConvert('value', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$IntegrationWhenToJson(IntegrationWhen instance) =>
    <String, dynamic>{
      'key': instance.key,
      'op': _$IntegrationWhenOpEnumEnumMap[instance.op]!,
      'value': instance.value,
    };

const _$IntegrationWhenOpEnumEnumMap = {
  IntegrationWhenOpEnum.eq: 'eq',
  IntegrationWhenOpEnum.neq: 'neq',
  IntegrationWhenOpEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
