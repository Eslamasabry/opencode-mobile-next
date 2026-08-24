// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Pty _$PtyFromJson(Map<String, dynamic> json) =>
    $checkedCreate('Pty', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'title',
          'command',
          'args',
          'cwd',
          'status',
          'pid',
        ],
      );
      final val = Pty(
        id: $checkedConvert('id', (v) => v as String),
        title: $checkedConvert('title', (v) => v as String),
        command: $checkedConvert('command', (v) => v as String),
        args: $checkedConvert(
          'args',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
        cwd: $checkedConvert('cwd', (v) => v as String),
        status: $checkedConvert(
          'status',
          (v) => $enumDecode(
            _$PtyStatusEnumEnumMap,
            v,
            unknownValue: PtyStatusEnum.unknownDefaultOpenApi,
          ),
        ),
        pid: $checkedConvert('pid', (v) => (v as num).toInt()),
        exitCode: $checkedConvert('exitCode', (v) => (v as num?)?.toInt()),
      );
      return val;
    });

Map<String, dynamic> _$PtyToJson(Pty instance) => <String, dynamic>{
  'id': instance.id,
  'title': instance.title,
  'command': instance.command,
  'args': instance.args,
  'cwd': instance.cwd,
  'status': _$PtyStatusEnumEnumMap[instance.status]!,
  'pid': instance.pid,
  'exitCode': ?instance.exitCode,
};

const _$PtyStatusEnumEnumMap = {
  PtyStatusEnum.running: 'running',
  PtyStatusEnum.exited: 'exited',
  PtyStatusEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
