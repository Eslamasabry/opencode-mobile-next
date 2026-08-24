// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_state_running.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolStateRunning _$ToolStateRunningFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ToolStateRunning', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['status', 'input', 'time']);
      final val = ToolStateRunning(
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$ToolStateRunningStatusEnumEnumMap,
            v,
            unknownValue: ToolStateRunningStatusEnum.unknownDefaultOpenApi,
          ),
        ),
        input: $checkedConvert('input', (v) => v as Object),
        title: $checkedConvert('title', (v) => v as String?),
        metadata: $checkedConvert('metadata', (v) => v),
        time: $checkedConvert(
          'time',
          (v) => ToolStateRunningTime.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ToolStateRunningToJson(ToolStateRunning instance) =>
    <String, dynamic>{
      'status': _$ToolStateRunningStatusEnumEnumMap[instance.status]!,
      'input': instance.input,
      'title': ?instance.title,
      'metadata': ?instance.metadata,
      'time': instance.time.toJson(),
    };

const _$ToolStateRunningStatusEnumEnumMap = {
  ToolStateRunningStatusEnum.running: 'running',
  ToolStateRunningStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
