// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union041_any_of1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion041AnyOf1 _$OpencodeSdkRawUnion041AnyOf1FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion041AnyOf1', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['success', 'error']);
  final val = OpencodeSdkRawUnion041AnyOf1(
    success: $checkedConvert(
      'success',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion041AnyOf1SuccessEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion041AnyOf1SuccessEnum.unknownDefaultOpenApi,
      ),
    ),
    error: $checkedConvert('error', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion041AnyOf1ToJson(
  OpencodeSdkRawUnion041AnyOf1 instance,
) => <String, dynamic>{
  'success':
      _$OpencodeSdkRawUnion041AnyOf1SuccessEnumEnumMap[instance.success]!,
  'error': instance.error,
};

const _$OpencodeSdkRawUnion041AnyOf1SuccessEnumEnumMap = {
  OpencodeSdkRawUnion041AnyOf1SuccessEnum.false_: 'false',
  OpencodeSdkRawUnion041AnyOf1SuccessEnum.unknownDefaultOpenApi: '11184809',
};
