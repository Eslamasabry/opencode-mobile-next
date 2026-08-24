// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_update_request_size.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyUpdateRequestSize _$PtyUpdateRequestSizeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PtyUpdateRequestSize', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['rows', 'cols']);
  final val = PtyUpdateRequestSize(
    rows: $checkedConvert('rows', (v) => (v as num).toInt()),
    cols: $checkedConvert('cols', (v) => (v as num).toInt()),
  );
  return val;
});

Map<String, dynamic> _$PtyUpdateRequestSizeToJson(
  PtyUpdateRequestSize instance,
) => <String, dynamic>{'rows': instance.rows, 'cols': instance.cols};
