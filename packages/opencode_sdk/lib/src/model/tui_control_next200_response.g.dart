// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tui_control_next200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TuiControlNext200Response _$TuiControlNext200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('TuiControlNext200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['path', 'body']);
  final val = TuiControlNext200Response(
    path: $checkedConvert('path', (v) => v as String),
    body: $checkedConvert('body', (v) => v),
  );
  return val;
});

Map<String, dynamic> _$TuiControlNext200ResponseToJson(
  TuiControlNext200Response instance,
) => <String, dynamic>{'path': instance.path, 'body': instance.body};
