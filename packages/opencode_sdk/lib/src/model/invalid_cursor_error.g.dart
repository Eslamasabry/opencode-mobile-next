// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'invalid_cursor_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

InvalidCursorError _$InvalidCursorErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('InvalidCursorError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['_tag', 'message']);
      final val = InvalidCursorError(
        tag: $checkedConvert(
          '_tag',
          (v) => $enumDecode(
            _$InvalidCursorErrorTagEnumEnumMap,
            v,
            unknownValue: InvalidCursorErrorTagEnum.unknownDefaultOpenApi,
          ),
        ),
        message: $checkedConvert('message', (v) => v as String),
      );
      return val;
    }, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$InvalidCursorErrorToJson(InvalidCursorError instance) =>
    <String, dynamic>{
      '_tag': _$InvalidCursorErrorTagEnumEnumMap[instance.tag]!,
      'message': instance.message,
    };

const _$InvalidCursorErrorTagEnumEnumMap = {
  InvalidCursorErrorTagEnum.invalidCursorError: 'InvalidCursorError',
  InvalidCursorErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
