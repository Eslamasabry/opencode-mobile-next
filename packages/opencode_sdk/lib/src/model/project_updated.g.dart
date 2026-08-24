// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectUpdated _$ProjectUpdatedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProjectUpdated', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = ProjectUpdated(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$ProjectUpdatedTypeEnumEnumMap,
            v,
            unknownValue: ProjectUpdatedTypeEnum.unknownDefaultOpenApi,
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
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => ProjectUpdatedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ProjectUpdatedToJson(ProjectUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$ProjectUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$ProjectUpdatedTypeEnumEnumMap = {
  ProjectUpdatedTypeEnum.projectPeriodUpdated: 'project.updated',
  ProjectUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
