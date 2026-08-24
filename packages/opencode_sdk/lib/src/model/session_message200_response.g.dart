// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_message200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionMessage200Response _$SessionMessage200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionMessage200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['info', 'parts']);
  final val = SessionMessage200Response(
    info: $checkedConvert('info', (v) => Message.fromJson(v)),
    parts: $checkedConvert(
      'parts',
      (v) => (v as List<dynamic>).map(ModelPart.fromJson).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionMessage200ResponseToJson(
  SessionMessage200Response instance,
) => <String, dynamic>{
  'info': instance.info.toJson(),
  'parts': instance.parts.map((e) => e.toJson()).toList(),
};
