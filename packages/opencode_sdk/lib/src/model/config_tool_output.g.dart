// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_tool_output.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigToolOutput _$ConfigToolOutputFromJson(Map<String, dynamic> json) =>
    $checkedCreate('ConfigToolOutput', json, ($checkedConvert) {
      final val = ConfigToolOutput(
        maxLines: $checkedConvert('max_lines', (v) => (v as num?)?.toInt()),
        maxBytes: $checkedConvert('max_bytes', (v) => (v as num?)?.toInt()),
      );
      return val;
    }, fieldKeyMap: const {'maxLines': 'max_lines', 'maxBytes': 'max_bytes'});

Map<String, dynamic> _$ConfigToolOutputToJson(ConfigToolOutput instance) =>
    <String, dynamic>{
      'max_lines': ?instance.maxLines,
      'max_bytes': ?instance.maxBytes,
    };
