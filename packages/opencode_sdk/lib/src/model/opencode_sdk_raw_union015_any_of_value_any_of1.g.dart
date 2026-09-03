// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union015_any_of_value_any_of1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion015AnyOfValueAnyOf1
_$OpencodeSdkRawUnion015AnyOfValueAnyOf1FromJson(Map<String, dynamic> json) =>
    $checkedCreate('OpencodeSdkRawUnion015AnyOfValueAnyOf1', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['command']);
      final val = OpencodeSdkRawUnion015AnyOfValueAnyOf1(
        command: $checkedConvert(
          'command',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
        extensions: $checkedConvert(
          'extensions',
          (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
        ),
        disabled: $checkedConvert('disabled', (v) => v as bool?),
        env: $checkedConvert(
          'env',
          (v) => (v as Map<String, dynamic>?)?.map(
            (k, e) => MapEntry(k, e as String),
          ),
        ),
        initialization: $checkedConvert('initialization', (v) => v),
      );
      return val;
    });

Map<String, dynamic> _$OpencodeSdkRawUnion015AnyOfValueAnyOf1ToJson(
  OpencodeSdkRawUnion015AnyOfValueAnyOf1 instance,
) => <String, dynamic>{
  'command': instance.command,
  'extensions': ?instance.extensions,
  'disabled': ?instance.disabled,
  'env': ?instance.env,
  'initialization': ?instance.initialization,
};
