// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_state_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolStateError _$ToolStateErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ToolStateError', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['status', 'input', 'error', 'time'],
      );
      final val = ToolStateError(
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$ToolStateErrorStatusEnumEnumMap,
            v,
            unknownValue: ToolStateErrorStatusEnum.unknownDefaultOpenApi,
          ),
        ),
        input: $checkedConvert('input', (v) => v as Object),
        error: $checkedConvert('error', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        time: $checkedConvert(
          'time',
          (v) => ToolStateErrorTime.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$ToolStateErrorToJson(ToolStateError instance) =>
    <String, dynamic>{
      'status': _$ToolStateErrorStatusEnumEnumMap[instance.status]!,
      'input': instance.input,
      'error': instance.error,
      'metadata': ?instance.metadata,
      'time': instance.time.toJson(),
    };

const _$ToolStateErrorStatusEnumEnumMap = {
  ToolStateErrorStatusEnum.error: 'error',
  ToolStateErrorStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
