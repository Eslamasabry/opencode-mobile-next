// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union042_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion042AnyOf _$OpencodeSdkRawUnion042AnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion042AnyOf', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['success', 'version']);
  final val = OpencodeSdkRawUnion042AnyOf(
    success: $checkedConvert(
      'success',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion042AnyOfSuccessEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion042AnyOfSuccessEnum.unknownDefaultOpenApi,
      ),
    ),
    version: $checkedConvert('version', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion042AnyOfToJson(
  OpencodeSdkRawUnion042AnyOf instance,
) => <String, dynamic>{
  'success': _$OpencodeSdkRawUnion042AnyOfSuccessEnumEnumMap[instance.success]!,
  'version': instance.version,
};

const _$OpencodeSdkRawUnion042AnyOfSuccessEnumEnumMap = {
  OpencodeSdkRawUnion042AnyOfSuccessEnum.true_: 'true',
  OpencodeSdkRawUnion042AnyOfSuccessEnum.unknownDefaultOpenApi: '11184809',
};
