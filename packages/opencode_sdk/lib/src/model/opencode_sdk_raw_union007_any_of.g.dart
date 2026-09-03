// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union007_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion007AnyOf _$OpencodeSdkRawUnion007AnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion007AnyOf', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['field']);
  final val = OpencodeSdkRawUnion007AnyOf(
    field: $checkedConvert(
      'field',
      (v) =>
          OpencodeSdkRawUnion007AnyOfField.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion007AnyOfToJson(
  OpencodeSdkRawUnion007AnyOf instance,
) => <String, dynamic>{'field': instance.field.toJson()};
