// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'workspace_warp_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorkspaceWarpError _$WorkspaceWarpErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorkspaceWarpError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'data']);
      final val = WorkspaceWarpError(
        name: $checkedConvert(
          'name',
          (v) => $enumDecode(
            _$WorkspaceWarpErrorNameEnumEnumMap,
            v,
            unknownValue: WorkspaceWarpErrorNameEnum.unknownDefaultOpenApi,
          ),
        ),
        data: $checkedConvert(
          'data',
          (v) => MoveSessionErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$WorkspaceWarpErrorToJson(WorkspaceWarpError instance) =>
    <String, dynamic>{
      'name': _$WorkspaceWarpErrorNameEnumEnumMap[instance.name]!,
      'data': instance.data.toJson(),
    };

const _$WorkspaceWarpErrorNameEnumEnumMap = {
  WorkspaceWarpErrorNameEnum.workspaceWarpError: 'WorkspaceWarpError',
  WorkspaceWarpErrorNameEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
