// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_project_copy_create_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2ProjectCopyCreateRequest _$V2ProjectCopyCreateRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2ProjectCopyCreateRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['strategy', 'directory']);
  final val = V2ProjectCopyCreateRequest(
    strategy: $checkedConvert('strategy', (v) => v as String),
    directory: $checkedConvert('directory', (v) => v as String),
    name: $checkedConvert('name', (v) => v as String?),
  );
  return val;
});

Map<String, dynamic> _$V2ProjectCopyCreateRequestToJson(
  V2ProjectCopyCreateRequest instance,
) => <String, dynamic>{
  'strategy': instance.strategy,
  'directory': instance.directory,
  'name': ?instance.name,
};
