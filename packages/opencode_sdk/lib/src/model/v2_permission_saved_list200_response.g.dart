// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_permission_saved_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2PermissionSavedList200Response _$V2PermissionSavedList200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2PermissionSavedList200Response', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['data']);
  final val = V2PermissionSavedList200Response(
    data: $checkedConvert(
      'data',
      (v) => (v as List<dynamic>)
          .map((e) => PermissionSavedInfo.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2PermissionSavedList200ResponseToJson(
  V2PermissionSavedList200Response instance,
) => <String, dynamic>{'data': instance.data.map((e) => e.toJson()).toList()};
