// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_prompt200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionPrompt200Response _$V2SessionPrompt200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionPrompt200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['data']);
  final val = V2SessionPrompt200Response(
    data: $checkedConvert(
      'data',
      (v) => SessionInputAdmitted.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SessionPrompt200ResponseToJson(
  V2SessionPrompt200Response instance,
) => <String, dynamic>{'data': instance.data.toJson()};
