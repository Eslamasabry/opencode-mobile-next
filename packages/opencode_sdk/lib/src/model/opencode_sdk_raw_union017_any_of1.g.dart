// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union017_any_of1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion017AnyOf1 _$OpencodeSdkRawUnion017AnyOf1FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion017AnyOf1', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'key', 'message', 'options']);
  final val = OpencodeSdkRawUnion017AnyOf1(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion017AnyOf1TypeEnumEnumMap,
        v,
        unknownValue:
            OpencodeSdkRawUnion017AnyOf1TypeEnum.unknownDefaultOpenApi,
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
          : OpencodeSdkRawUnion017AnyOfWhen.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
}, fieldKeyMap: const {'when_': 'when'});

Map<String, dynamic> _$OpencodeSdkRawUnion017AnyOf1ToJson(
  OpencodeSdkRawUnion017AnyOf1 instance,
) => <String, dynamic>{
  'type': _$OpencodeSdkRawUnion017AnyOf1TypeEnumEnumMap[instance.type]!,
  'key': instance.key,
  'message': instance.message,
  'options': instance.options.map((e) => e.toJson()).toList(),
  'when': ?instance.when_?.toJson(),
};

const _$OpencodeSdkRawUnion017AnyOf1TypeEnumEnumMap = {
  OpencodeSdkRawUnion017AnyOf1TypeEnum.select: 'select',
  OpencodeSdkRawUnion017AnyOf1TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
