// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_directories_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectDirectoriesUpdated _$ProjectDirectoriesUpdatedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProjectDirectoriesUpdated', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
  final val = ProjectDirectoriesUpdated(
    id: $checkedConvert('id', (v) => v as String),
    metadata: $checkedConvert('metadata', (v) => v),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$ProjectDirectoriesUpdatedTypeEnumEnumMap,
        v,
        unknownValue: ProjectDirectoriesUpdatedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    durable: $checkedConvert(
      'durable',
      (v) => v == null
          ? null
          : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
    ),
    location: $checkedConvert(
      'location',
      (v) => v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => ProjectDirectoriesUpdatedData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$ProjectDirectoriesUpdatedToJson(
  ProjectDirectoriesUpdated instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'type': _$ProjectDirectoriesUpdatedTypeEnumEnumMap[instance.type]!,
  'durable': ?instance.durable?.toJson(),
  'location': ?instance.location?.toJson(),
  'data': instance.data.toJson(),
};

const _$ProjectDirectoriesUpdatedTypeEnumEnumMap = {
  ProjectDirectoriesUpdatedTypeEnum.projectPeriodDirectoriesPeriodUpdated:
      'project.directories.updated',
  ProjectDirectoriesUpdatedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
