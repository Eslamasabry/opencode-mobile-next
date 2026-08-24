// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'workspace_status_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorkspaceStatusData _$WorkspaceStatusDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorkspaceStatusData', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['workspaceID', 'status']);
      final val = WorkspaceStatusData(
        workspaceID: $checkedConvert('workspaceID', (v) => v as String?),
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$WorkspaceStatusDataStatusEnumEnumMap,
            v,
            unknownValue: WorkspaceStatusDataStatusEnum.unknownDefaultOpenApi,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$WorkspaceStatusDataToJson(
  WorkspaceStatusData instance,
) => <String, dynamic>{
  'workspaceID': instance.workspaceID,
  'status': _$WorkspaceStatusDataStatusEnumEnumMap[instance.status]!,
};

const _$WorkspaceStatusDataStatusEnumEnumMap = {
  WorkspaceStatusDataStatusEnum.connected: 'connected',
  WorkspaceStatusDataStatusEnum.connecting: 'connecting',
  WorkspaceStatusDataStatusEnum.disconnected: 'disconnected',
  WorkspaceStatusDataStatusEnum.error: 'error',
  WorkspaceStatusDataStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
