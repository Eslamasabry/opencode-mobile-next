// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'api_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

APIError _$APIErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('APIError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = APIError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$APIErrorNameEnumEnumMap,
            v,
            unknownValue: APIErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => APIErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$APIErrorToJson(APIError instance) => <String, dynamic>{
  'name': _$APIErrorNameEnumEnumMap[instance.name]!,
  'data': instance.data.toJson(),
};

const _$APIErrorNameEnumEnumMap = {
  APIErrorNameEnum.aPIError: 'APIError',
  APIErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
