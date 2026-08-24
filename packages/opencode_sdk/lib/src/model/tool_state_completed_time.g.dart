// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_state_completed_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolStateCompletedTime _$ToolStateCompletedTimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ToolStateCompletedTime', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['start', 'end']);
  final val = ToolStateCompletedTime(
    start: $checkedConvert('start', (v) => (v as num).toInt()),
    end: $checkedConvert('end', (v) => (v as num).toInt()),
    compacted: $checkedConvert('compacted', (v) => (v as num?)?.toInt()),
  );
  return val;
});

Map<String, dynamic> _$ToolStateCompletedTimeToJson(
  ToolStateCompletedTime instance,
) => <String, dynamic>{
  'start': instance.start,
  'end': instance.end,
  'compacted': ?instance.compacted,
};
