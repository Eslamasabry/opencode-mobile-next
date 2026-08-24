// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_permission_request_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2PermissionRequestList200Response _$V2PermissionRequestList200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2PermissionRequestList200Response', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['location', 'data']);
  final val = V2PermissionRequestList200Response(
    location: $checkedConvert(
      'location',
      (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => (v as List<dynamic>)
          .map((e) => PermissionV2Request.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2PermissionRequestList200ResponseToJson(
  V2PermissionRequestList200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.map((e) => e.toJson()).toList(),
};
