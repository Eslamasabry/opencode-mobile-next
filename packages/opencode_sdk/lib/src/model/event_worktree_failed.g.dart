// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_worktree_failed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventWorktreeFailed _$EventWorktreeFailedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventWorktreeFailed', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventWorktreeFailed(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventWorktreeFailedTypeEnumEnumMap,
            v,
            unknownValue: EventWorktreeFailedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => MoveSessionErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventWorktreeFailedToJson(
  EventWorktreeFailed instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventWorktreeFailedTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventWorktreeFailedTypeEnumEnumMap = {
  EventWorktreeFailedTypeEnum.worktreePeriodFailed: 'worktree.failed',
  EventWorktreeFailedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
