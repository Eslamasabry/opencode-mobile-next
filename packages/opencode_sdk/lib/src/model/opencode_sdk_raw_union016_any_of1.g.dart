// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union016_any_of1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion016AnyOf1 _$OpencodeSdkRawUnion016AnyOf1FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion016AnyOf1', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'key', 'message', 'options']);
  final val = OpencodeSdkRawUnion016AnyOf1(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion016AnyOf1TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion016AnyOf1TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    key: $checkedConvert('key', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
    options: $checkedConvert(
      'options',
      (v) => (v as List<dynamic>)
          .map(
            (e) => IntegrationSelectPromptOptionsInner.fromJson(
              e as Map<String, dynamic>,
            ),
          )
          .toList(),
    ),
    when_: $checkedConvert(
      'when',
      (v) => v == null
          ? null
          : OpencodeSdkRawUnion016AnyOfWhen.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
}, fieldKeyMap: const {'when_': 'when'});

Map<String, dynamic> _$OpencodeSdkRawUnion016AnyOf1ToJson(
  OpencodeSdkRawUnion016AnyOf1 instance,
) => <String, dynamic>{
  'type': _$OpencodeSdkRawUnion016AnyOf1TypeEnumEnumMap[instance.type]!,
  'key': instance.key,
  'message': instance.message,
  'options': instance.options.map((e) => e.toJson()).toList(),
  'when': ?instance.when_?.toJson(),
};

const _$OpencodeSdkRawUnion016AnyOf1TypeEnumEnumMap = {
  OpencodeSdkRawUnion016AnyOf1TypeEnum.select: 'select',
  OpencodeSdkRawUnion016AnyOf1TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
