// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_workspace_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventWorkspaceFailed _$EventWorkspaceFailedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventWorkspaceFailed', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventWorkspaceFailed(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventWorkspaceFailedTypeEnumEnumMap,
        v,
        unknownValue: EventWorkspaceFailedTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => MoveSessionErrorData.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventWorkspaceFailedToJson(
  EventWorkspaceFailed instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventWorkspaceFailedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventWorkspaceFailedTypeEnumEnumMap = {
  EventWorkspaceFailedTypeEnum.workspacePeriodFailed: 'workspace.failed',
  EventWorkspaceFailedTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
