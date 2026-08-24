// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'conflict_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConflictError _$ConflictErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ConflictError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['_tag', 'message']);
      final val = ConflictError(
        tag: $checkedConvert(
          '_tag',
          (v) => $enumDecode(
            _$ConflictErrorTagEnumEnumMap,
            v,
            unknownValue: ConflictErrorTagEnum.unknownDefaultOpenApi,
          ),
        ),
        message: $checkedConvert('message', (v) => v as String),
        resource: $checkedConvert('resource', (v) => v as String?),
      );
      return val;
    }, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$ConflictErrorToJson(ConflictError instance) =>
    <String, dynamic>{
      '_tag': _$ConflictErrorTagEnumEnumMap[instance.tag]!,
      'message': instance.message,
      'resource': ?instance.resource,
    };

const _$ConflictErrorTagEnumEnumMap = {
  ConflictErrorTagEnum.conflictError: 'ConflictError',
  ConflictErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
