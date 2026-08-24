// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'invalid_request_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

InvalidRequestError _$InvalidRequestErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('InvalidRequestError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['_tag', 'message']);
      final val = InvalidRequestError(
        tag: $checkedConvert(
          '_tag',
          (v) => $enumDecode(
            _$InvalidRequestErrorTagEnumEnumMap,
            v,
            unknownValue: InvalidRequestErrorTagEnum.unknownDefaultOpenApi,
          ),
        ),
        message: $checkedConvert('message', (v) => v as String),
        kind: $checkedConvert('kind', (v) => v as String?),
        field: $checkedConvert('field', (v) => v as String?),
      );
      return val;
    }, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$InvalidRequestErrorToJson(
  InvalidRequestError instance,
) => <String, dynamic>{
  '_tag': _$InvalidRequestErrorTagEnumEnumMap[instance.tag]!,
  'message': instance.message,
  'kind': ?instance.kind,
  'field': ?instance.field,
};

const _$InvalidRequestErrorTagEnumEnumMap = {
  InvalidRequestErrorTagEnum.invalidRequestError: 'InvalidRequestError',
  InvalidRequestErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
