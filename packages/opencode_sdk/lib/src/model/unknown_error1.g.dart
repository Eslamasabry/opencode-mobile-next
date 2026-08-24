// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'unknown_error1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UnknownError1 _$UnknownError1FromJson(Map<String, dynamic> json) =>
    $checkedCreate('UnknownError1', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['_tag', 'message']);
      final val = UnknownError1(
        tag: $checkedConvert(
          '_tag',
          (v) => $enumDecode(
            _$UnknownError1TagEnumEnumMap,
            v,
            unknownValue: UnknownError1TagEnum.unknownDefaultOpenApi,
          ),
        ),
        message: $checkedConvert('message', (v) => v as String),
        ref: $checkedConvert('ref', (v) => v as String?),
      );
      return val;
    }, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$UnknownError1ToJson(UnknownError1 instance) =>
    <String, dynamic>{
      '_tag': _$UnknownError1TagEnumEnumMap[instance.tag]!,
      'message': instance.message,
      'ref': ?instance.ref,
    };

const _$UnknownError1TagEnumEnumMap = {
  UnknownError1TagEnum.unknownError: 'UnknownError',
  UnknownError1TagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
