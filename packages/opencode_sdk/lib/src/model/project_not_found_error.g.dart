// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_not_found_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectNotFoundError _$ProjectNotFoundErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProjectNotFoundError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['_tag', 'projectID', 'message']);
  final val = ProjectNotFoundError(
    tag: $checkedConvert(
      '_tag',
      (v) => $enumDecode(
        _$ProjectNotFoundErrorTagEnumEnumMap,
        v,
        unknownValue: ProjectNotFoundErrorTagEnum.unknownDefaultOpenApi,
      ),
    ),
    projectID: $checkedConvert('projectID', (v) => v as String),
    message: $checkedConvert('message', (v) => v as String),
  );
  return val;
}, fieldKeyMap: const {'tag': '_tag'});

Map<String, dynamic> _$ProjectNotFoundErrorToJson(
  ProjectNotFoundError instance,
) => <String, dynamic>{
  '_tag': _$ProjectNotFoundErrorTagEnumEnumMap[instance.tag]!,
  'projectID': instance.projectID,
  'message': instance.message,
};

const _$ProjectNotFoundErrorTagEnumEnumMap = {
  ProjectNotFoundErrorTagEnum.projectNotFoundError: 'ProjectNotFoundError',
  ProjectNotFoundErrorTagEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
