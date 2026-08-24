// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tui_execute_command_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TuiExecuteCommandRequest _$TuiExecuteCommandRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('TuiExecuteCommandRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['command']);
  final val = TuiExecuteCommandRequest(
    command: $checkedConvert('command', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$TuiExecuteCommandRequestToJson(
  TuiExecuteCommandRequest instance,
) => <String, dynamic>{'command': instance.command};
