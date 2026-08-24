// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_state_pending.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolStatePending _$ToolStatePendingFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ToolStatePending', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['status', 'input', 'raw']);
      final val = ToolStatePending(
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$ToolStatePendingStatusEnumEnumMap,
            v,
            unknownValue: ToolStatePendingStatusEnum.unknownDefaultOpenApi,
          ),
        ),
        input: $checkedConvert('input', (v) => v as Object),
        raw: $checkedConvert('raw', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$ToolStatePendingToJson(ToolStatePending instance) =>
    <String, dynamic>{
      'status': _$ToolStatePendingStatusEnumEnumMap[instance.status]!,
      'input': instance.input,
      'raw': instance.raw,
    };

const _$ToolStatePendingStatusEnumEnumMap = {
  ToolStatePendingStatusEnum.pending: 'pending',
  ToolStatePendingStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
