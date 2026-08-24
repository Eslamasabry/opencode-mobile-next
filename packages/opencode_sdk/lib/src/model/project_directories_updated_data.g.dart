// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_directories_updated_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectDirectoriesUpdatedData _$ProjectDirectoriesUpdatedDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProjectDirectoriesUpdatedData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['projectID']);
  final val = ProjectDirectoriesUpdatedData(
    projectID: $checkedConvert('projectID', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$ProjectDirectoriesUpdatedDataToJson(
  ProjectDirectoriesUpdatedData instance,
) => <String, dynamic>{'projectID': instance.projectID};
