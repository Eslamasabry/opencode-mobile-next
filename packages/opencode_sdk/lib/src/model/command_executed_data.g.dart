// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'command_executed_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

CommandExecutedData _$CommandExecutedDataFromJson(Map<String, dynamic> json) =>
    $checkedCreate('CommandExecutedData', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['name', 'sessionID', 'arguments', 'messageID'],
      );
      final val = CommandExecutedData(
        name: $checkedConvert('name', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        arguments: $checkedConvert('arguments', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$CommandExecutedDataToJson(
  CommandExecutedData instance,
) => <String, dynamic>{
  'name': instance.name,
  'sessionID': instance.sessionID,
  'arguments': instance.arguments,
  'messageID': instance.messageID,
};
