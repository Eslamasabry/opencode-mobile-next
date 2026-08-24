// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_tui_command_execute.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTuiCommandExecute _$EventTuiCommandExecuteFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('EventTuiCommandExecute', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['type', 'properties']);
  final val = EventTuiCommandExecute(
    type: $checkedConvert(
      'type',
      (v) => $enumDecode(
        _$EventTuiCommandExecuteTypeEnumEnumMap,
        v,
        unknownValue: EventTuiCommandExecuteTypeEnum.unknownDefaultOpenApi,
      ),
    ),
    properties: $checkedConvert(
      'properties',
      (v) =>
          EventTuiCommandExecuteProperties.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$EventTuiCommandExecuteToJson(
  EventTuiCommandExecute instance,
) => <String, dynamic>{
  'type': _$EventTuiCommandExecuteTypeEnumEnumMap[instance.type]!,
  'properties': instance.properties.toJson(),
};

const _$EventTuiCommandExecuteTypeEnumEnumMap = {
  EventTuiCommandExecuteTypeEnum.tuiPeriodCommandPeriodExecute:
      'tui.command.execute',
  EventTuiCommandExecuteTypeEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
