// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_shell200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionShell200Response _$SessionShell200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionShell200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['info', 'parts']);
  final val = SessionShell200Response(
    info: $checkedConvert('info', (v) => Message.fromJson(v)),
    parts: $checkedConvert(
      'parts',
      (v) => (v as List<dynamic>).map(ModelPart.fromJson).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionShell200ResponseToJson(
  SessionShell200Response instance,
) => <String, dynamic>{
  'info': instance.info.toJson(),
  'parts': instance.parts.map((e) => e.toJson()).toList(),
};
