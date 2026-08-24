// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'forbidden_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ForbiddenError _$ForbiddenErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ForbiddenError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['_tag', 'message']);
      final val = ForbiddenError(
        tag: $checkedConvert(
          '_tag',
          (v) => $enumDecode(
            _$ForbiddenErrorTagEnumEnumMap,
            v,
            unknownValue: ForbiddenErrorTagEnum.unknownDefaultOpenApi,
          ),
        ),
        message: $checkedConvert('message', (v) => v as String),
      );
      return val;
    }, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$ForbiddenErrorToJson(ForbiddenError instance) =>
    <String, dynamic>{
      '_tag': _$ForbiddenErrorTagEnumEnumMap[instance.tag]!,
      'message': instance.message,
    };

const _$ForbiddenErrorTagEnumEnumMap = {
  ForbiddenErrorTagEnum.forbiddenError: 'ForbiddenError',
  ForbiddenErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
