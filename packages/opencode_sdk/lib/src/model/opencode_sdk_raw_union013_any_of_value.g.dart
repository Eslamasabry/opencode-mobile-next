// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union013_any_of_value.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion013AnyOfValue _$OpencodeSdkRawUnion013AnyOfValueFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion013AnyOfValue', json, (
  $checkedConvert,
) {
  final val = OpencodeSdkRawUnion013AnyOfValue(
    disabled: $checkedConvert('disabled', (v) => v as bool?),
    command: $checkedConvert(
      'command',
      (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
    ),
    environment: $checkedConvert(
      'environment',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as String)),
    ),
    extensions: $checkedConvert(
      'extensions',
      (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion013AnyOfValueToJson(
  OpencodeSdkRawUnion013AnyOfValue instance,
) => <String, dynamic>{
  'disabled': ?instance.disabled,
  'command': ?instance.command,
  'environment': ?instance.environment,
  'extensions': ?instance.extensions,
};
