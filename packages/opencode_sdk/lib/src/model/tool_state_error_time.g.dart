// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_state_error_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolStateErrorTime _$ToolStateErrorTimeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ToolStateErrorTime', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['start', 'end']);
      final val = ToolStateErrorTime(
        start: $checkedConvert('start', (v) => (v as num).toInt()),
        end: $checkedConvert('end', (v) => (v as num).toInt()),
      );
      return val;
    });

Map<String, dynamic> _$ToolStateErrorTimeToJson(ToolStateErrorTime instance) =>
    <String, dynamic>{'start': instance.start, 'end': instance.end};
