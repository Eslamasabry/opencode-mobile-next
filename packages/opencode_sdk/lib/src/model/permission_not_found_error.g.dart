// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_not_found_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionNotFoundError _$PermissionNotFoundErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PermissionNotFoundError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['_tag', 'requestID', 'message']);
  final val = PermissionNotFoundError(
    tag: $checkedConvert(
      '_tag',
      (v) => $enumDecode(
        _$PermissionNotFoundErrorTagEnumEnumMap,
        v,
        unknownValue: PermissionNotFoundErrorTagEnum.unknownDefaultOpenApi,
      ),
    ),
    requestID: $checkedConvert('requestID', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
  );
  return val;
}, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$PermissionNotFoundErrorToJson(
  PermissionNotFoundError instance,
) => <String, dynamic>{
  '_tag': _$PermissionNotFoundErrorTagEnumEnumMap[instance.tag]!,
  'requestID': instance.requestID,
  'message': instance.message,
};

const _$PermissionNotFoundErrorTagEnumEnumMap = {
  PermissionNotFoundErrorTagEnum.permissionNotFoundError:
      'PermissionNotFoundError',
  PermissionNotFoundErrorTagEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
