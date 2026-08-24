// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'not_found_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

NotFoundError _$NotFoundErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('NotFoundError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = NotFoundError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$NotFoundErrorNameEnumEnumMap,
            v,
            unknownValue: NotFoundErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => NotFoundErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$NotFoundErrorToJson(NotFoundError instance) =>
    <String, dynamic>{
      'name': _$NotFoundErrorNameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$NotFoundErrorNameEnumEnumMap = {
  NotFoundErrorNameEnum.notFoundError: 'NotFoundError',
  NotFoundErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
