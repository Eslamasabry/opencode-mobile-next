// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_project_copy_remove_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2ProjectCopyRemoveRequest _$V2ProjectCopyRemoveRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2ProjectCopyRemoveRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['directory', 'force']);
  final val = V2ProjectCopyRemoveRequest(
    directory: $checkedConvert('directory', (v) => v as String),
    force: $checkedConvert('force', (v) => v as bool),
  );
  return val;
});

Map<String, dynamic> _$V2ProjectCopyRemoveRequestToJson(
  V2ProjectCopyRemoveRequest instance,
) => <String, dynamic>{
  'directory': instance.directory,
  'force': instance.force,
};
