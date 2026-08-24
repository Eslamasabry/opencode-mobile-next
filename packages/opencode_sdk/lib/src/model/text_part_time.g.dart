// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'text_part_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TextPartTime _$TextPartTimeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('TextPartTime', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['start']);
      final val = TextPartTime(
        start: $checkedConvert('start', (v) => (v as num).toInt()),
        end: $checkedConvert('end', (v) => (v as num?)?.toInt()),
      );
      return val;
    });

Map<String, dynamic> _$TextPartTimeToJson(TextPartTime instance) =>
    <String, dynamic>{'start': instance.start, 'end': ?instance.end};
