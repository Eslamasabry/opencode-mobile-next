// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_create_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyCreateRequest _$PtyCreateRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PtyCreateRequest', json, ($checkedConvert) {
      final val = PtyCreateRequest(
        command: $checkedConvert('command', (v) => v as String?),
        args: $checkedConvert(
          'args',
          (v) => (v as List<dynamic>?)?.map((e) => e as String).toList(),
        ),
        cwd: $checkedConvert('cwd', (v) => v as String?),
        title: $checkedConvert('title', (v) => v as String?),
        env: $checkedConvert(
          'env',
          (v) => (v as Map<String, dynamic>?)?.map(
            (k, e) => MapEntry(k, e as String),
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PtyCreateRequestToJson(PtyCreateRequest instance) =>
    <String, dynamic>{
      'command': ?instance.command,
      'args': ?instance.args,
      'cwd': ?instance.cwd,
      'title': ?instance.title,
      'env': ?instance.env,
    };
