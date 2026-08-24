// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_shells200_response_inner.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyShells200ResponseInner _$PtyShells200ResponseInnerFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('PtyShells200ResponseInner', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['path', 'name', 'acceptable']);
  final val = PtyShells200ResponseInner(
    path: $checkedConvert('path', (v) => v as String),
    name: $checkedConvert('name', (v) => v as String),
    acceptable: $checkedConvert('acceptable', (v) => v as bool),
  );
  return val;
});

Map<String, dynamic> _$PtyShells200ResponseInnerToJson(
  PtyShells200ResponseInner instance,
) => <String, dynamic>{
  'path': instance.path,
  'name': instance.name,
  'acceptable': instance.acceptable,
};
