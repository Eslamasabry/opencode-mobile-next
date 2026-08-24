// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_state_completed.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolStateCompleted _$ToolStateCompletedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ToolStateCompleted', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'status',
          'input',
          'output',
          'title',
          'metadata',
          'time',
        ],
      );
      final val = ToolStateCompleted(
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$ToolStateCompletedStatusEnumEnumMap,
            v,
            unknownValue: ToolStateCompletedStatusEnum.unknownDefaultOpenApi,
          ),
        ),
        input: $checkedConvert('input', (v) => v as Object),
        output: $checkedConvert('output', (v) => v as String),
        title: $checkedConvert('title', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v as Object),
        time: $checkedConvert(
          'time',
          (v) => ToolStateCompletedTime.fromJson(v as Map<String, dynamic>),
        ),
        attachments: $checkedConvert(
          'attachments',
          (v) => (v as List<dynamic>?)
              ?.map((e) => FilePart.fromJson(e as Map<String, dynamic>))
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ToolStateCompletedToJson(ToolStateCompleted instance) =>
    <String, dynamic>{
      'status': _$ToolStateCompletedStatusEnumEnumMap[instance.status]!,
      'input': instance.input,
      'output': instance.output,
      'title': instance.title,
      'metadata': instance.metadata,
      'time': instance.time.toJson(),
      'attachments': ?instance.attachments?.map((e) => e.toJson()).toList(),
    };

const _$ToolStateCompletedStatusEnumEnumMap = {
  ToolStateCompletedStatusEnum.completed: 'completed',
  ToolStateCompletedStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
