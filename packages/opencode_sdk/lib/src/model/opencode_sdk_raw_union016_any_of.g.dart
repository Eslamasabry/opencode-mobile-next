// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union016_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion016AnyOf _$OpencodeSdkRawUnion016AnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion016AnyOf', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'key', 'message']);
  final val = OpencodeSdkRawUnion016AnyOf(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion016AnyOfTypeEnumEnumMap,
        v,
        unknownValue: OpencodeSdkRawUnion016AnyOfTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    key: $checkedConvert('key', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
    placeholder: $checkedConvert('placeholder', (v) => v as String?),
    when_: $checkedConvert(
      'when',
      (v) => v == null
          ? null
          : OpencodeSdkRawUnion016AnyOfWhen.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
}, fieldKeyMap: const {'when_': 'when'});

Map<String, dynamic> _$OpencodeSdkRawUnion016AnyOfToJson(
  OpencodeSdkRawUnion016AnyOf instance,
) => <String, dynamic>{
  'type': _$OpencodeSdkRawUnion016AnyOfTypeEnumEnumMap[instance.type]!,
  'key': instance.key,
  'message': instance.message,
  'placeholder': ?instance.placeholder,
  'when': ?instance.when_?.toJson(),
};

const _$OpencodeSdkRawUnion016AnyOfTypeEnumEnumMap = {
  OpencodeSdkRawUnion016AnyOfTypeEnum.text: 'text',
  OpencodeSdkRawUnion016AnyOfTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
