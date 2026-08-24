// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union006_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion006AnyOf _$OpencodeSdkRawUnion006AnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion006AnyOf', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['field']);
  final val = OpencodeSdkRawUnion006AnyOf(
    field: $checkedConvert(
      'field',
      (v) =>
          OpencodeSdkRawUnion006AnyOfField.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion006AnyOfToJson(
  OpencodeSdkRawUnion006AnyOf instance,
) => <String, dynamic>{'field': instance.field.toJson()};
