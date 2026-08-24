// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_copy_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectCopyError _$ProjectCopyErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ProjectCopyError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = ProjectCopyError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$ProjectCopyErrorNameEnumEnumMap,
            v,
            unknownValue: ProjectCopyErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => ProjectCopyErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ProjectCopyErrorToJson(ProjectCopyError instance) =>
    <String, dynamic>{
      'name': _$ProjectCopyErrorNameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$ProjectCopyErrorNameEnumEnumMap = {
  ProjectCopyErrorNameEnum.projectCopyError: 'ProjectCopyError',
  ProjectCopyErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
