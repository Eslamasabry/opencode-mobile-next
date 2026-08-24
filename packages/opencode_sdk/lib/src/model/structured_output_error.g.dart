// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'structured_output_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

StructuredOutputError _$StructuredOutputErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('StructuredOutputError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['name', 'data']);
  final val = StructuredOutputError(
    name: $checkedConvert(
      'name',
      (v) => $enumDecode(
        _$StructuredOutputErrorNameEnumEnumMap,
        v,
        unknownValue: StructuredOutputErrorNameEnum.unknownDefaultOpenApi,
      ),
    ),
    data: $checkedConvert(
      'data',
      (v) => StructuredOutputErrorData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$StructuredOutputErrorToJson(
  StructuredOutputError instance,
) => <String, dynamic>{
  'name': _$StructuredOutputErrorNameEnumEnumMap[instance.name]!,
  'data': instance.data.toJson(),
};

const _$StructuredOutputErrorNameEnumEnumMap = {
  StructuredOutputErrorNameEnum.structuredOutputError: 'StructuredOutputError',
  StructuredOutputErrorNameEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
