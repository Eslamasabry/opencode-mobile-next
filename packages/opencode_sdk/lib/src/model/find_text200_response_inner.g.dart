// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'find_text200_response_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FindText200ResponseInner _$FindText200ResponseInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate(
  'FindText200ResponseInner',
  json,
  ($checkedConvert) {
    $checkKeys(
      json,
      requiredKeys: const [
        'path',
        'lines',
        'line_number',
        'absolute_offset',
        'submatches',
      ],
    );
    final val = FindText200ResponseInner(
      path: $checkedConvert(
        'path',
        (v) => FindText200ResponseInnerPath.fromJson(v as Map<String, dynamic>),
      ),
      lines: $checkedConvert(
        'lines',
        (v) => FindText200ResponseInnerPath.fromJson(v as Map<String, dynamic>),
      ),
      lineNumber: $checkedConvert('line_number', (v) => (v as num).toInt()),
      absoluteOffset: $checkedConvert(
        'absolute_offset',
        (v) => (v as num).toInt(),
      ),
      submatches: $checkedConvert(
        'submatches',
        (v) => (v as List<dynamic>)
            .map(
              (e) => FindText200ResponseInnerSubmatchesInner.fromJson(
                e as Map<String, dynamic>,
              ),
            )
            .toList(),
      ),
    );
    return val;
  },
  fieldKeyMap: const {
    'lineNumber': 'line_number',
    'absoluteOffset': 'absolute_offset',
  },
);

Map<String, dynamic> _$FindText200ResponseInnerToJson(
  FindText200ResponseInner instance,
) => <String, dynamic>{
  'path': instance.path.toJson(),
  'lines': instance.lines.toJson(),
  'line_number': instance.lineNumber,
  'absolute_offset': instance.absoluteOffset,
  'submatches': instance.submatches.map((e) => e.toJson()).toList(),
};
