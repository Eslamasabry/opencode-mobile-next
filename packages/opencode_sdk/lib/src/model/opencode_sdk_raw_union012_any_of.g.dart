// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union012_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion012AnyOf _$OpencodeSdkRawUnion012AnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion012AnyOf', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['enabled']);
  final val = OpencodeSdkRawUnion012AnyOf(
    enabled: $checkedConvert('enabled', (v) => v as bool),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion012AnyOfToJson(
  OpencodeSdkRawUnion012AnyOf instance,
) => <String, dynamic>{'enabled': instance.enabled};
