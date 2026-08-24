// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'project_copy_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ProjectCopyErrorData _$ProjectCopyErrorDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ProjectCopyErrorData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['message']);
  final val = ProjectCopyErrorData(
    message: $checkedConvert('message', (v) => v as String),
    forceRequired: $checkedConvert('forceRequired', (v) => v as bool?),
  );
  return val;
});

Map<String, dynamic> _$ProjectCopyErrorDataToJson(
  ProjectCopyErrorData instance,
) => <String, dynamic>{
  'message': instance.message,
  'forceRequired': ?instance.forceRequired,
};
