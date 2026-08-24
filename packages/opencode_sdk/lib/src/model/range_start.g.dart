// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'range_start.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

RangeStart _$RangeStartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('RangeStart', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['line', 'character']);
      final val = RangeStart(
        line: $checkedConvert('line', (v) => (v as num).toInt()),
        character: $checkedConvert('character', (v) => (v as num).toInt()),
      );
      return val;
    });

Map<String, dynamic> _$RangeStartToJson(RangeStart instance) =>
    <String, dynamic>{'line': instance.line, 'character': instance.character};
