// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_workspace_ready.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventWorkspaceReady _$EventWorkspaceReadyFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventWorkspaceReady', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventWorkspaceReady(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventWorkspaceReadyTypeEnumEnumMap,
            v,
            unknownValue: EventWorkspaceReadyTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => ExperimentalProjectCopyGenerateName200Response.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventWorkspaceReadyToJson(
  EventWorkspaceReady instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventWorkspaceReadyTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventWorkspaceReadyTypeEnumEnumMap = {
  EventWorkspaceReadyTypeEnum.workspacePeriodReady: 'workspace.ready',
  EventWorkspaceReadyTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
