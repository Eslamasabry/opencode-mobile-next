// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union017_any_of_when.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion017AnyOfWhen _$OpencodeSdkRawUnion017AnyOfWhenFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion017AnyOfWhen', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['key', 'op', 'value']);
  final val = OpencodeSdkRawUnion017AnyOfWhen(
    key: $checkedConvert('key', (v) => v as String),
    op: $checkedConvert(
      'op',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion017AnyOfWhenOpEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion017AnyOfWhenOpEnum.unknownDefaultOpenApi,
      ),
    ),
    value: $checkedConvert('value', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion017AnyOfWhenToJson(
  OpencodeSdkRawUnion017AnyOfWhen instance,
) => <String, dynamic>{
  'key': instance.key,
  'op': _$OpencodeSdkRawUnion017AnyOfWhenOpEnumEnumMap[instance.op]!,
  'value': instance.value,
};

const _$OpencodeSdkRawUnion017AnyOfWhenOpEnumEnumMap = {
  OpencodeSdkRawUnion017AnyOfWhenOpEnum.eq: 'eq',
  OpencodeSdkRawUnion017AnyOfWhenOpEnum.neq: 'neq',
  OpencodeSdkRawUnion017AnyOfWhenOpEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
