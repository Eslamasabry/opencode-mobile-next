// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_permission_create200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionPermissionCreate200Response
_$V2SessionPermissionCreate200ResponseFromJson(Map<String, dynamic> json) =>
    $checkedCreate('V2SessionPermissionCreate200Response', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['data']);
      final val = V2SessionPermissionCreate200Response(
        data: $checkedConvert(
          'data',
          (v) => V2SessionPermissionCreate200ResponseData.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$V2SessionPermissionCreate200ResponseToJson(
  V2SessionPermissionCreate200Response instance,
) => <String, dynamic>{'data': instance.data.toJson()};
