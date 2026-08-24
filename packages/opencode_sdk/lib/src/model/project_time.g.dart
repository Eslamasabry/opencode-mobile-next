// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectTime _$ProjectTimeFromJson(Map<String, dynamic> json) => $checkedCreate(
  'ProjectTime',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['created', 'updated']);
    final val = ProjectTime(
      created: $checkedConvert('created', (v) => (v as num).toInt()),
      updated: $checkedConvert('updated', (v) => (v as num).toInt()),
      initialized: $checkedConvert('initialized', (v) => (v as num?)?.toInt()),
    );
    return val;
  },
);

Map<String, dynamic> _$ProjectTimeToJson(ProjectTime instance) =>
    <String, dynamic>{
      'created': instance.created,
      'updated': instance.updated,
      'initialized': ?instance.initialized,
    };
