// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'workspace_event_connection_status.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorkspaceEventConnectionStatus _$WorkspaceEventConnectionStatusFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('WorkspaceEventConnectionStatus', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['workspaceID', 'status']);
  final val = WorkspaceEventConnectionStatus(
    workspaceID: $checkedConvert('workspaceID', (v) => v as String?),
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$WorkspaceEventConnectionStatusStatusEnumEnumMap,
        v,
        unknownValue:
            WorkspaceEventConnectionStatusStatusEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$WorkspaceEventConnectionStatusToJson(
  WorkspaceEventConnectionStatus instance,
) => <String, dynamic>{
  'workspaceID': instance.workspaceID,
  'status': _$WorkspaceEventConnectionStatusStatusEnumEnumMap[instance.status]!,
};

const _$WorkspaceEventConnectionStatusStatusEnumEnumMap = {
  WorkspaceEventConnectionStatusStatusEnum.connected: 'connected',
  WorkspaceEventConnectionStatusStatusEnum.connecting: 'connecting',
  WorkspaceEventConnectionStatusStatusEnum.disconnected: 'disconnected',
  WorkspaceEventConnectionStatusStatusEnum.error: 'error',
  WorkspaceEventConnectionStatusStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
