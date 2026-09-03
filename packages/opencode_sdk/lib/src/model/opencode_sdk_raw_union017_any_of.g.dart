// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union017_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion017AnyOf _$OpencodeSdkRawUnion017AnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion017AnyOf', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'key', 'message']);
  final val = OpencodeSdkRawUnion017AnyOf(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion017AnyOfTypeEnumEnumMap,
        v,
        unknownValue: OpencodeSdkRawUnion017AnyOfTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    key: $checkedConvert('key', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
    placeholder: $checkedConvert('placeholder', (v) => v as String?),
    when_: $checkedConvert(
      'when',
      (v) => v == null
          ? null
          : OpencodeSdkRawUnion017AnyOfWhen.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
}, fieldKeyMap: const {'when_': 'when'});

Map<String, dynamic> _$OpencodeSdkRawUnion017AnyOfToJson(
  OpencodeSdkRawUnion017AnyOf instance,
) => <String, dynamic>{
  'type': _$OpencodeSdkRawUnion017AnyOfTypeEnumEnumMap[instance.type]!,
  'key': instance.key,
  'message': instance.message,
  'placeholder': ?instance.placeholder,
  'when': ?instance.when_?.toJson(),
};

const _$OpencodeSdkRawUnion017AnyOfTypeEnumEnumMap = {
  OpencodeSdkRawUnion017AnyOfTypeEnum.text: 'text',
  OpencodeSdkRawUnion017AnyOfTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
