// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_permission_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionPermissionList200Response _$V2SessionPermissionList200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionPermissionList200Response', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['data']);
  final val = V2SessionPermissionList200Response(
    data: $checkedConvert(
      'data',
      (v) => (v as List<dynamic>)
          .map((e) => PermissionV2Request.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SessionPermissionList200ResponseToJson(
  V2SessionPermissionList200Response instance,
) => <String, dynamic>{'data': instance.data.map((e) => e.toJson()).toList()};
