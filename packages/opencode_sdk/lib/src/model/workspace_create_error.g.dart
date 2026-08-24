// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'workspace_create_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorkspaceCreateError _$WorkspaceCreateErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('WorkspaceCreateError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['name', 'data']);
  final val = WorkspaceCreateError(
    name: $checkedConvert(
      'name',
      (v) => $enumDecode(
        _$WorkspaceCreateErrorNameEnumEnumMap,
        v,
        unknownValue: WorkspaceCreateErrorNameEnum.unknownDefaultOpenApi,
      ),
    ),
    data: $checkedConvert(
      'data',
      (v) => MoveSessionErrorData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$WorkspaceCreateErrorToJson(
  WorkspaceCreateError instance,
) => <String, dynamic>{
  'name': _$WorkspaceCreateErrorNameEnumEnumMap[instance.name]!,
  'data': instance.data.toJson(),
};

const _$WorkspaceCreateErrorNameEnumEnumMap = {
  WorkspaceCreateErrorNameEnum.workspaceCreateError: 'WorkspaceCreateError',
  WorkspaceCreateErrorNameEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
