// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'range.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Range _$RangeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('Range', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['start', 'end']);
      final val = Range(
        start: $checkedConvert(
          'start',
          (v) => RangeStart.fromJson(v as Map<String, dynamic>),
        ),
        end: $checkedConvert(
          'end',
          (v) => RangeStart.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$RangeToJson(Range instance) => <String, dynamic>{
  'start': instance.start.toJson(),
  'end': instance.end.toJson(),
};
