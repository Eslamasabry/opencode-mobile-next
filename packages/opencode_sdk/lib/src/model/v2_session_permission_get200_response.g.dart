// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_permission_get200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionPermissionGet200Response _$V2SessionPermissionGet200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionPermissionGet200Response', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['data']);
  final val = V2SessionPermissionGet200Response(
    data: $checkedConvert(
      'data',
      (v) => PermissionV2Request.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SessionPermissionGet200ResponseToJson(
  V2SessionPermissionGet200Response instance,
) => <String, dynamic>{'data': instance.data.toJson()};
