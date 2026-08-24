// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_workspace_status_properties.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventWorkspaceStatusProperties _$EventWorkspaceStatusPropertiesFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventWorkspaceStatusProperties', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['workspaceID', 'status']);
  final val = EventWorkspaceStatusProperties(
    workspaceID: $checkedConvert('workspaceID', (v) => v as String),
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$EventWorkspaceStatusPropertiesStatusEnumEnumMap,
        v,
        unknownValue:
            EventWorkspaceStatusPropertiesStatusEnum.unknownDefaultOpenApi,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventWorkspaceStatusPropertiesToJson(
  EventWorkspaceStatusProperties instance,
) => <String, dynamic>{
  'workspaceID': instance.workspaceID,
  'status': _$EventWorkspaceStatusPropertiesStatusEnumEnumMap[instance.status]!,
};

const _$EventWorkspaceStatusPropertiesStatusEnumEnumMap = {
  EventWorkspaceStatusPropertiesStatusEnum.connected: 'connected',
  EventWorkspaceStatusPropertiesStatusEnum.connecting: 'connecting',
  EventWorkspaceStatusPropertiesStatusEnum.disconnected: 'disconnected',
  EventWorkspaceStatusPropertiesStatusEnum.error: 'error',
  EventWorkspaceStatusPropertiesStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
