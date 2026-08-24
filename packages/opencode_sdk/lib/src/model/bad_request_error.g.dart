// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'bad_request_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BadRequestError _$BadRequestErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('BadRequestError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = BadRequestError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$BadRequestErrorNameEnumEnumMap,
            v,
            unknownValue: BadRequestErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => BadRequestErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$BadRequestErrorToJson(BadRequestError instance) =>
    <String, dynamic>{
      'name': _$BadRequestErrorNameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$BadRequestErrorNameEnumEnumMap = {
  BadRequestErrorNameEnum.badRequest: 'BadRequest',
  BadRequestErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
