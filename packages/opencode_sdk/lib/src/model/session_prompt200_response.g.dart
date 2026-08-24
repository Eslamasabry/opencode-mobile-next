// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_prompt200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionPrompt200Response _$SessionPrompt200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionPrompt200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['info', 'parts']);
  final val = SessionPrompt200Response(
    info: $checkedConvert(
      'info',
      (v) => AssistantMessage.fromJson(v as Map<String, dynamic>),
    ),
    parts: $checkedConvert(
      'parts',
      (v) => (v as List<dynamic>).map(ModelPart.fromJson).toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionPrompt200ResponseToJson(
  SessionPrompt200Response instance,
) => <String, dynamic>{
  'info': instance.info.toJson(),
  'parts': instance.parts.map((e) => e.toJson()).toList(),
};
