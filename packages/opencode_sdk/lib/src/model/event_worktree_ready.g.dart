// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_worktree_ready.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventWorktreeReady _$EventWorktreeReadyFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventWorktreeReady', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventWorktreeReady(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventWorktreeReadyTypeEnumEnumMap,
            v,
            unknownValue: EventWorktreeReadyTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => WorktreeReadyData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventWorktreeReadyToJson(EventWorktreeReady instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventWorktreeReadyTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventWorktreeReadyTypeEnumEnumMap = {
  EventWorktreeReadyTypeEnum.worktreePeriodReady: 'worktree.ready',
  EventWorktreeReadyTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
