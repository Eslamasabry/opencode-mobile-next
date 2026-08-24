// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'unauthorized_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UnauthorizedError _$UnauthorizedErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('UnauthorizedError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['_tag', 'message']);
      final val = UnauthorizedError(
        tag: $checkedConvert(
          '_tag',
          (v) => $enumDecode(
            _$UnauthorizedErrorTagEnumEnumMap,
            v,
            unknownValue: UnauthorizedErrorTagEnum.unknownDefaultOpenApi,
          ),
        ),
        message: $checkedConvert('message', (v) => v as String),
      );
      return val;
    }, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$UnauthorizedErrorToJson(UnauthorizedError instance) =>
    <String, dynamic>{
      '_tag': _$UnauthorizedErrorTagEnumEnumMap[instance.tag]!,
      'message': instance.message,
    };

const _$UnauthorizedErrorTagEnumEnumMap = {
  UnauthorizedErrorTagEnum.unauthorizedError: 'UnauthorizedError',
  UnauthorizedErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
