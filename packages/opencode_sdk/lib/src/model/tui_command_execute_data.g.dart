// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tui_command_execute_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TuiCommandExecuteData _$TuiCommandExecuteDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('TuiCommandExecuteData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['command']);
  final val = TuiCommandExecuteData(
    command: $checkedConvert(
      'command',
      (v) => OpencodeSdkRawUnion035.fromJson(v),
    ),
  );
  return val;
});

Map<String, dynamic> _$TuiCommandExecuteDataToJson(
  TuiCommandExecuteData instance,
) => <String, dynamic>{'command': instance.command.toJson()};
