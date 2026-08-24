// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_not_found_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyNotFoundError _$PtyNotFoundErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PtyNotFoundError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['_tag', 'ptyID', 'message']);
      final val = PtyNotFoundError(
        tag: $checkedConvert(
          '_tag',
          (v) => $enumDecode(
            _$PtyNotFoundErrorTagEnumEnumMap,
            v,
            unknownValue: PtyNotFoundErrorTagEnum.unknownDefaultOpenApi,
          ),
        ),
        ptyID: $checkedConvert('ptyID', (v) => v as String),
        message: $checkedConvert('message', (v) => v as String),
      );
      return val;
    }, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$PtyNotFoundErrorToJson(PtyNotFoundError instance) =>
    <String, dynamic>{
      '_tag': _$PtyNotFoundErrorTagEnumEnumMap[instance.tag]!,
      'ptyID': instance.ptyID,
      'message': instance.message,
    };

const _$PtyNotFoundErrorTagEnumEnumMap = {
  PtyNotFoundErrorTagEnum.ptyNotFoundError: 'PtyNotFoundError',
  PtyNotFoundErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
