// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of45_properties_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf45PropertiesError
_$OpencodeSdkRawUnion002AnyOf45PropertiesErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('OpencodeSdkRawUnion002AnyOf45PropertiesError', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['name', 'data']);
  final val = OpencodeSdkRawUnion002AnyOf45PropertiesError(
    name: $checkedConvert(
      'name',
      (v) => $enumDecode(
        _$OpencodeSdkRawUnion002AnyOf45PropertiesErrorNameEnumEnumMap,
        v,
        unknownValue: OpencodeSdkRawUnion002AnyOf45PropertiesErrorNameEnum
            .unknownDefaultOpenApi,
      ),
    ),
    data: $checkedConvert(
      'data',
      (v) => APIErrorData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf45PropertiesErrorToJson(
  OpencodeSdkRawUnion002AnyOf45PropertiesError instance,
) => <String, dynamic>{
  'name':
      _$OpencodeSdkRawUnion002AnyOf45PropertiesErrorNameEnumEnumMap[instance
          .name]!,
  'data': instance.data.toJson(),
};

const _$OpencodeSdkRawUnion002AnyOf45PropertiesErrorNameEnumEnumMap = {
  OpencodeSdkRawUnion002AnyOf45PropertiesErrorNameEnum.aPIError: 'APIError',
  OpencodeSdkRawUnion002AnyOf45PropertiesErrorNameEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
