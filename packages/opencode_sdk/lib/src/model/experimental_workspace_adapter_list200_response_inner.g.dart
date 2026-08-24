// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'experimental_workspace_adapter_list200_response_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ExperimentalWorkspaceAdapterList200ResponseInner
_$ExperimentalWorkspaceAdapterList200ResponseInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ExperimentalWorkspaceAdapterList200ResponseInner', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['type', 'name', 'description']);
  final val = ExperimentalWorkspaceAdapterList200ResponseInner(
    type: $checkedConvert('type', (v) => v as String),
    name: $checkedConvert('name', (v) => v as String),
    description: $checkedConvert('description', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$ExperimentalWorkspaceAdapterList200ResponseInnerToJson(
  ExperimentalWorkspaceAdapterList200ResponseInner instance,
) => <String, dynamic>{
  'type': instance.type,
  'name': instance.name,
  'description': instance.description,
};
