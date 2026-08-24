// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'permission_asked_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PermissionAskedData _$PermissionAskedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PermissionAskedData', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'sessionID',
          'permission',
          'patterns',
          'metadata',
          'always',
        ],
      );
      final val = PermissionAskedData(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        permission: $checkedConvert('permission', (v) => v as String),
        patterns: $checkedConvert(
          'patterns',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
        metadata: $checkedConvert('metadata', (v) => v as Object),
        always: $checkedConvert(
          'always',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
        tool: $checkedConvert(
          'tool',
          (v) => v == null
              ? null
              : PermissionRequestTool.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PermissionAskedDataToJson(
  PermissionAskedData instance,
) => <String, dynamic>{
  'id': instance.id,
  'sessionID': instance.sessionID,
  'permission': instance.permission,
  'patterns': instance.patterns,
  'metadata': instance.metadata,
  'always': instance.always,
  'tool': ?instance.tool?.toJson(),
};
