// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_tui_command_execute_schema2.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTuiCommandExecuteSchema2 _$EventTuiCommandExecuteSchema2FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventTuiCommandExecuteSchema2', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
  final val = EventTuiCommandExecuteSchema2(
    id: $checkedConvert('id', (v) => v as String),
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventTuiCommandExecuteSchema2TypeEnumEnumMap,
        v,
        unknownValue:
            EventTuiCommandExecuteSchema2TypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) => EventTuiCommandExecuteSchema2Properties.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventTuiCommandExecuteSchema2ToJson(
  EventTuiCommandExecuteSchema2 instance,
) => <String, dynamic>{
  'id': instance.id,
  'type': _$EventTuiCommandExecuteSchema2TypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventTuiCommandExecuteSchema2TypeEnumEnumMap = {
  EventTuiCommandExecuteSchema2TypeEnum.tuiPeriodCommandPeriodExecute:
      'tui.command.execute',
  EventTuiCommandExecuteSchema2TypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
