// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message_shell.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessageShell _$SessionMessageShellFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionMessageShell', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'time',
          'type',
          'callID',
          'command',
          'output',
        ],
      );
      final val = SessionMessageShell(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        time: $checkedConvert(
          'time',
          (v) => SessionMessageShellTime.fromJson(v as Map<String, dynamic>),
        ),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionMessageShellTypeEnumEnumMap,
            v,
            unknownValue: SessionMessageShellTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        callID: $checkedConvert('callID', (v) => v as String),
        command: $checkedConvert('command', (v) => v as String),
        output: $checkedConvert('output', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$SessionMessageShellToJson(
  SessionMessageShell instance,
) => <String, dynamic>{
  'id': instance.id,
  'metadata': ?instance.metadata,
  'time': instance.time.toJson(),
  'type': _$SessionMessageShellTypeEnumEnumMap[instance.type]!,
  'callID': instance.callID,
  'command': instance.command,
  'output': instance.output,
};

const _$SessionMessageShellTypeEnumEnumMap = {
  SessionMessageShellTypeEnum.shell: 'shell',
  SessionMessageShellTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
