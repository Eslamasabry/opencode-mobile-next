// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union016_any_of_when.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion016AnyOfWhen _$OpencodeSdkRawUnion016AnyOfWhenFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion016AnyOfWhen', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['key', 'op', 'value']);
  final val = OpencodeSdkRawUnion016AnyOfWhen(
    key: $checkedConvert('key', (v) => v as String),
    op: $checkedConvert(
      'op',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion016AnyOfWhenOpEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion016AnyOfWhenOpEnum.unknownDefaultOpenApi,
      ),
    ),
    value: $checkedConvert('value', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion016AnyOfWhenToJson(
  OpencodeSdkRawUnion016AnyOfWhen instance,
) => <String, dynamic>{
  'key': instance.key,
  'op': _$OpencodeSdkRawUnion016AnyOfWhenOpEnumEnumMap[instance.op]!,
  'value': instance.value,
};

const _$OpencodeSdkRawUnion016AnyOfWhenOpEnumEnumMap = {
  OpencodeSdkRawUnion016AnyOfWhenOpEnum.eq: 'eq',
  OpencodeSdkRawUnion016AnyOfWhenOpEnum.neq: 'neq',
  OpencodeSdkRawUnion016AnyOfWhenOpEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
