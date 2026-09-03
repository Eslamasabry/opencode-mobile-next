// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union013_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion013AnyOf _$OpencodeSdkRawUnion013AnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion013AnyOf', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['enabled']);
  final val = OpencodeSdkRawUnion013AnyOf(
    enabled: $checkedConvert('enabled', (v) => v as bool),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion013AnyOfToJson(
  OpencodeSdkRawUnion013AnyOf instance,
) => <String, dynamic>{'enabled': instance.enabled};
