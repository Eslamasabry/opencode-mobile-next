// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'workspace_status.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorkspaceStatus _$WorkspaceStatusFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorkspaceStatus', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = WorkspaceStatus(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$WorkspaceStatusTypeEnumEnumMap,
            v,
            unknownValue: WorkspaceStatusTypeEnum.unknownDefaultOpenApi,
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
          (v) => WorkspaceStatusData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$WorkspaceStatusToJson(WorkspaceStatus instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$WorkspaceStatusTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$WorkspaceStatusTypeEnumEnumMap = {
  WorkspaceStatusTypeEnum.workspacePeriodStatus: 'workspace.status',
  WorkspaceStatusTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
