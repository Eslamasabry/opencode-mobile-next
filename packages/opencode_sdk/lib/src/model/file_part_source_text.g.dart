// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'file_part_source_text.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FilePartSourceText _$FilePartSourceTextFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FilePartSourceText', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['value', 'start', 'end']);
      final val = FilePartSourceText(
        value: $checkedConvert('value', (v) => v as String),
        start: $checkedConvert('start', (v) => v as num),
        end: $checkedConvert('end', (v) => v as num),
      );
      return val;
    });

Map<String, dynamic> _$FilePartSourceTextToJson(FilePartSourceText instance) =>
    <String, dynamic>{
      'value': instance.value,
      'start': instance.start,
      'end': instance.end,
    };
