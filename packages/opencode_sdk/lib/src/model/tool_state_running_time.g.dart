// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tool_state_running_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ToolStateRunningTime _$ToolStateRunningTimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('ToolStateRunningTime', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['start']);
  final val = ToolStateRunningTime(
    start: $checkedConvert('start', (v) => (v as num).toInt()),
  );
  return val;
});

Map<String, dynamic> _$ToolStateRunningTimeToJson(
  ToolStateRunningTime instance,
) => <String, dynamic>{'start': instance.start};
