// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'worktree_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorktreeFailed _$WorktreeFailedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorktreeFailed', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = WorktreeFailed(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$WorktreeFailedTypeEnumEnumMap,
            v,
            unknownValue: WorktreeFailedTypeEnum.unknownDefaultOpenApi,
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
          (v) => MoveSessionErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$WorktreeFailedToJson(WorktreeFailed instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$WorktreeFailedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$WorktreeFailedTypeEnumEnumMap = {
  WorktreeFailedTypeEnum.worktreePeriodFailed: 'worktree.failed',
  WorktreeFailedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
