// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_saved_info.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionSavedInfo _$PermissionSavedInfoFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PermissionSavedInfo', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['id', 'projectID', 'action', 'resource'],
      );
      final val = PermissionSavedInfo(
        id: $checkedConvert('id', (v) => v as String),
        projectID: $checkedConvert('projectID', (v) => v as String),
        action: $checkedConvert('action', (v) => v as String),
        resource: $checkedConvert('resource', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$PermissionSavedInfoToJson(
  PermissionSavedInfo instance,
) => <String, dynamic>{
  'id': instance.id,
  'projectID': instance.projectID,
  'action': instance.action,
  'resource': instance.resource,
};
