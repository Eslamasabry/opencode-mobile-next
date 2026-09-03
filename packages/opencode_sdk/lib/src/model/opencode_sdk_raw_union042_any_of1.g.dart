// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union042_any_of1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion042AnyOf1 _$OpencodeSdkRawUnion042AnyOf1FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion042AnyOf1', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['success', 'error']);
  final val = OpencodeSdkRawUnion042AnyOf1(
    success: $checkedConvert(
      'success',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion042AnyOf1SuccessEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion042AnyOf1SuccessEnum.unknownDefaultOpenApi,
      ),
    ),
    error: $checkedConvert('error', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion042AnyOf1ToJson(
  OpencodeSdkRawUnion042AnyOf1 instance,
) => <String, dynamic>{
  'success':
      _$OpencodeSdkRawUnion042AnyOf1SuccessEnumEnumMap[instance.success]!,
  'error': instance.error,
};

const _$OpencodeSdkRawUnion042AnyOf1SuccessEnumEnumMap = {
  OpencodeSdkRawUnion042AnyOf1SuccessEnum.false_: 'false',
  OpencodeSdkRawUnion042AnyOf1SuccessEnum.unknownDefaultOpenApi: '11184809',
};
