// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'unknown_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UnknownError _$UnknownErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('UnknownError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = UnknownError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$UnknownErrorNameEnumEnumMap,
            v,
            unknownValue: UnknownErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => UnknownErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$UnknownErrorToJson(UnknownError instance) =>
    <String, dynamic>{
      'name': _$UnknownErrorNameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$UnknownErrorNameEnumEnumMap = {
  UnknownErrorNameEnum.unknownError: 'UnknownError',
  UnknownErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
