// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union041_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion041AnyOf _$OpencodeSdkRawUnion041AnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion041AnyOf', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['success', 'version']);
  final val = OpencodeSdkRawUnion041AnyOf(
    success: $checkedConvert(
      'success',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion041AnyOfSuccessEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion041AnyOfSuccessEnum.unknownDefaultOpenApi,
      ),
    ),
    version: $checkedConvert('version', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion041AnyOfToJson(
  OpencodeSdkRawUnion041AnyOf instance,
) => <String, dynamic>{
  'success': _$OpencodeSdkRawUnion041AnyOfSuccessEnumEnumMap[instance.success]!,
  'version': instance.version,
};

const _$OpencodeSdkRawUnion041AnyOfSuccessEnumEnumMap = {
  OpencodeSdkRawUnion041AnyOfSuccessEnum.true_: 'true',
  OpencodeSdkRawUnion041AnyOfSuccessEnum.unknownDefaultOpenApi: '11184809',
};
