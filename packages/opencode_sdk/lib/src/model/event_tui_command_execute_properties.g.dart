// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_tui_command_execute_properties.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventTuiCommandExecuteProperties _$EventTuiCommandExecutePropertiesFromJson(
  Map<String, dynamic> json,
) =>
    $checkedCreate('EventTuiCommandExecuteProperties', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['command']);
      final val = EventTuiCommandExecuteProperties(
        command: $checkedConvert(
          'command',
          (v) => OpencodeSdkRawUnion018.fromJson(v),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventTuiCommandExecutePropertiesToJson(
  EventTuiCommandExecuteProperties instance,
) => <String, dynamic>{'command': instance.command.toJson()};
