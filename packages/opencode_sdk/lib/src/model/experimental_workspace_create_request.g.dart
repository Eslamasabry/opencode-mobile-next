// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'experimental_workspace_create_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ExperimentalWorkspaceCreateRequest _$ExperimentalWorkspaceCreateRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ExperimentalWorkspaceCreateRequest', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['type']);
  final val = ExperimentalWorkspaceCreateRequest(
    id: $checkedConvert('id', (v) => v as String?),
    type: $checkedConvert('type', (v) => v as String),
    branch: $checkedConvert('branch', (v) => v as String?),
    extra: $checkedConvert('extra', (v) => v),
  );
  return val;
});

Map<String, dynamic> _$ExperimentalWorkspaceCreateRequestToJson(
  ExperimentalWorkspaceCreateRequest instance,
) => <String, dynamic>{
  'id': ?instance.id,
  'type': instance.type,
  'branch': ?instance.branch,
  'extra': ?instance.extra,
};
