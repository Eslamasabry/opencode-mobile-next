// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'context_overflow_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ContextOverflowError _$ContextOverflowErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ContextOverflowError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['name', 'data']);
  final val = ContextOverflowError(
    name: $checkedConvert(
      'name',
      (v) => $enumDecode(
        _$ContextOverflowErrorNameEnumEnumMap,
        v,
        unknownValue: ContextOverflowErrorNameEnum.unknownDefaultOpenApi,
      ),
    ),
    data: $checkedConvert(
      'data',
      (v) => ContextOverflowErrorData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$ContextOverflowErrorToJson(
  ContextOverflowError instance,
) => <String, dynamic>{
  'name': _$ContextOverflowErrorNameEnumEnumMap[instance.name]!,
  'data': instance.data.toJson(),
};

const _$ContextOverflowErrorNameEnumEnumMap = {
  ContextOverflowErrorNameEnum.contextOverflowError: 'ContextOverflowError',
  ContextOverflowErrorNameEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
