// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union014_any_of_value_any_of1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion014AnyOfValueAnyOf1
_$OpencodeSdkRawUnion014AnyOfValueAnyOf1FromJson(Map<String, dynamic> json) =>
    $checkedCreate('OpencodeSdkRawUnion014AnyOfValueAnyOf1', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['command']);
      final val = OpencodeSdkRawUnion014AnyOfValueAnyOf1(
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

Map<String, dynamic> _$OpencodeSdkRawUnion014AnyOfValueAnyOf1ToJson(
  OpencodeSdkRawUnion014AnyOfValueAnyOf1 instance,
) => <String, dynamic>{
  'command': instance.command,
  'extensions': ?instance.extensions,
  'disabled': ?instance.disabled,
  'env': ?instance.env,
  'initialization': ?instance.initialization,
};
