// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'location_ref.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

LocationRef _$LocationRefFromJson(Map<String, dynamic> json) =>
    $checkedCreate('LocationRef', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['directory']);
      final val = LocationRef(
        directory: $checkedConvert('directory', (v) => v as String),
        workspaceID: $checkedConvert('workspaceID', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$LocationRefToJson(LocationRef instance) =>
    <String, dynamic>{
      'directory': instance.directory,
      'workspaceID': ?instance.workspaceID,
    };
