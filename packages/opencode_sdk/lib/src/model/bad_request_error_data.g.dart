// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'bad_request_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BadRequestErrorData _$BadRequestErrorDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('BadRequestErrorData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['message']);
      final val = BadRequestErrorData(
        message: $checkedConvert('message', (v) => v as String),
        kind: $checkedConvert(
          'kind',
          (v) => $enumDecodeNullable(
            _$BadRequestErrorDataKindEnumEnumMap,
            v,
            unknownValue: BadRequestErrorDataKindEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$BadRequestErrorDataToJson(
  BadRequestErrorData instance,
) => <String, dynamic>{
  'message': instance.message,
  'kind': ?_$BadRequestErrorDataKindEnumEnumMap[instance.kind],
};

const _$BadRequestErrorDataKindEnumEnumMap = {
  BadRequestErrorDataKindEnum.params: 'Params',
  BadRequestErrorDataKindEnum.headers: 'Headers',
  BadRequestErrorDataKindEnum.query: 'Query',
  BadRequestErrorDataKindEnum.body: 'Body',
  BadRequestErrorDataKindEnum.payload: 'Payload',
  BadRequestErrorDataKindEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
