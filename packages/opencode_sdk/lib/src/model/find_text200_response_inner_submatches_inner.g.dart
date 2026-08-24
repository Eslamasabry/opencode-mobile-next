// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'find_text200_response_inner_submatches_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FindText200ResponseInnerSubmatchesInner
_$FindText200ResponseInnerSubmatchesInnerFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FindText200ResponseInnerSubmatchesInner', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['match', 'start', 'end']);
      final val = FindText200ResponseInnerSubmatchesInner(
        match: $checkedConvert(
          'match',
          (v) =>
              FindText200ResponseInnerPath.fromJson(v as Map<String, dynamic>),
        ),
        start: $checkedConvert('start', (v) => (v as num).toInt()),
        end: $checkedConvert('end', (v) => (v as num).toInt()),
      );
      return val;
    });

Map<String, dynamic> _$FindText200ResponseInnerSubmatchesInnerToJson(
  FindText200ResponseInnerSubmatchesInner instance,
) => <String, dynamic>{
  'match': instance.match.toJson(),
  'start': instance.start,
  'end': instance.end,
};
