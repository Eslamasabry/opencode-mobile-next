// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union014_any_of_value_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion014AnyOfValueAnyOf
_$OpencodeSdkRawUnion014AnyOfValueAnyOfFromJson(Map<String, dynamic> json) =>
    $checkedCreate('OpencodeSdkRawUnion014AnyOfValueAnyOf', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['disabled']);
      final val = OpencodeSdkRawUnion014AnyOfValueAnyOf(
        disabled: $checkedConvert(
          'disabled',
          (v) => $enumDecode(
            _$OpencodeSdkRawUnion014AnyOfValueAnyOfDisabledEnumEnumMap,
            v,
            unknownValue: OpencodeSdkRawUnion014AnyOfValueAnyOfDisabledEnum
                .unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$OpencodeSdkRawUnion014AnyOfValueAnyOfToJson(
  OpencodeSdkRawUnion014AnyOfValueAnyOf instance,
) => <String, dynamic>{
  'disabled':
      _$OpencodeSdkRawUnion014AnyOfValueAnyOfDisabledEnumEnumMap[instance
          .disabled]!,
};

const _$OpencodeSdkRawUnion014AnyOfValueAnyOfDisabledEnumEnumMap = {
  OpencodeSdkRawUnion014AnyOfValueAnyOfDisabledEnum.true_: 'true',
  OpencodeSdkRawUnion014AnyOfValueAnyOfDisabledEnum.unknownDefaultOpenApi:
      '11184809',
};
