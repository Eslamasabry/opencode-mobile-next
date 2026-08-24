// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'workspace.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Workspace _$WorkspaceFromJson(Map<String, dynamic> json) =>
    $checkedCreate('Workspace', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['id', 'type', 'name', 'projectID', 'timeUsed'],
      );
      final val = Workspace(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert('type', (v) => v as String),
        name: $checkedConvert('name', (v) => v as String),
        branch: $checkedConvert('branch', (v) => v as String?),
        directory: $checkedConvert('directory', (v) => v as String?),
        extra: $checkedConvert('extra', (v) => v),
        projectID: $checkedConvert('projectID', (v) => v as String),
        timeUsed: $checkedConvert(
          'timeUsed',
          (v) => OpencodeSdkRawUnion018.fromJson(v),
        ),
      );
      return val;
    });

Map<String, dynamic> _$WorkspaceToJson(Workspace instance) => <String, dynamic>{
  'id': instance.id,
  'type': instance.type,
  'name': instance.name,
  'branch': ?instance.branch,
  'directory': ?instance.directory,
  'extra': ?instance.extra,
  'projectID': instance.projectID,
  'timeUsed': instance.timeUsed.toJson(),
};
