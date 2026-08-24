// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_forbidden_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyForbiddenError _$PtyForbiddenErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PtyForbiddenError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['_tag', 'message']);
      final val = PtyForbiddenError(
        tag: $checkedConvert(
          '_tag',
          (v) => $enumDecode(
            _$PtyForbiddenErrorTagEnumEnumMap,
            v,
            unknownValue: PtyForbiddenErrorTagEnum.unknownDefaultOpenApi,
          ),
        ),
        message: $checkedConvert('message', (v) => v as String),
      );
      return val;
    }, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$PtyForbiddenErrorToJson(PtyForbiddenError instance) =>
    <String, dynamic>{
      '_tag': _$PtyForbiddenErrorTagEnumEnumMap[instance.tag]!,
      'message': instance.message,
    };

const _$PtyForbiddenErrorTagEnumEnumMap = {
  PtyForbiddenErrorTagEnum.ptyForbiddenError: 'PtyForbiddenError',
  PtyForbiddenErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
