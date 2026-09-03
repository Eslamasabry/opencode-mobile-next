// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_tui_command_execute_schema2_properties.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTuiCommandExecuteSchema2Properties
_$EventTuiCommandExecuteSchema2PropertiesFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventTuiCommandExecuteSchema2Properties', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['command']);
      final val = EventTuiCommandExecuteSchema2Properties(
        command: $checkedConvert(
          'command',
          (v) => OpencodeSdkRawUnion020.fromJson(v),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventTuiCommandExecuteSchema2PropertiesToJson(
  EventTuiCommandExecuteSchema2Properties instance,
) => <String, dynamic>{'command': instance.command.toJson()};
