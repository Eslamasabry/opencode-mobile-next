// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'worktree_ready.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

WorktreeReady _$WorktreeReadyFromJson(Map<String, dynamic> json) =>
    $checkedCreate('WorktreeReady', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = WorktreeReady(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$WorktreeReadyTypeEnumEnumMap,
            v,
            unknownValue: WorktreeReadyTypeEnum.unknownDefaultOpenApi,
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
          (v) => WorktreeReadyData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$WorktreeReadyToJson(WorktreeReady instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$WorktreeReadyTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$WorktreeReadyTypeEnumEnumMap = {
  WorktreeReadyTypeEnum.worktreePeriodReady: 'worktree.ready',
  WorktreeReadyTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
