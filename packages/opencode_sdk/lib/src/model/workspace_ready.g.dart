// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'workspace_ready.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorkspaceReady _$WorkspaceReadyFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorkspaceReady', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = WorkspaceReady(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$WorkspaceReadyTypeEnumEnumMap,
            v,
            unknownValue: WorkspaceReadyTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        durable: $checkedConvert(
          'durable',
          (v) => v == null
              ? null
              : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
        ),
        location: $checkedConvert(
          'location',
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => ExperimentalProjectCopyGenerateName200Response.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$WorkspaceReadyToJson(WorkspaceReady instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$WorkspaceReadyTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$WorkspaceReadyTypeEnumEnumMap = {
  WorkspaceReadyTypeEnum.workspacePeriodReady: 'workspace.ready',
  WorkspaceReadyTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
