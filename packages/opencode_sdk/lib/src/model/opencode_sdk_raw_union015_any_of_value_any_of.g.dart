// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union015_any_of_value_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion015AnyOfValueAnyOf
_$OpencodeSdkRawUnion015AnyOfValueAnyOfFromJson(Map<String, dynamic> json) =>
    $checkedCreate('OpencodeSdkRawUnion015AnyOfValueAnyOf', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['disabled']);
      final val = OpencodeSdkRawUnion015AnyOfValueAnyOf(
        disabled: $checkedConvert(
          'disabled',
          (v) => $enumDecode(
            _$OpencodeSdkRawUnion015AnyOfValueAnyOfDisabledEnumEnumMap,
            v,
            unknownValue: OpencodeSdkRawUnion015AnyOfValueAnyOfDisabledEnum
                .unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$OpencodeSdkRawUnion015AnyOfValueAnyOfToJson(
  OpencodeSdkRawUnion015AnyOfValueAnyOf instance,
) => <String, dynamic>{
  'disabled':
      _$OpencodeSdkRawUnion015AnyOfValueAnyOfDisabledEnumEnumMap[instance
          .disabled]!,
};

const _$OpencodeSdkRawUnion015AnyOfValueAnyOfDisabledEnumEnumMap = {
  OpencodeSdkRawUnion015AnyOfValueAnyOfDisabledEnum.true_: 'true',
  OpencodeSdkRawUnion015AnyOfValueAnyOfDisabledEnum.unknownDefaultOpenApi:
      '11184809',
};
