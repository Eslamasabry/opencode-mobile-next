// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_workspace_status.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventWorkspaceStatus _$EventWorkspaceStatusFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventWorkspaceStatus', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventWorkspaceStatus(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventWorkspaceStatusTypeEnumEnumMap,
        v,
        unknownValue: EventWorkspaceStatusTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => EventWorkspaceStatusProperties.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventWorkspaceStatusToJson(
  EventWorkspaceStatus instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventWorkspaceStatusTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventWorkspaceStatusTypeEnumEnumMap = {
  EventWorkspaceStatusTypeEnum.workspacePeriodStatus: 'workspace.status',
  EventWorkspaceStatusTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
