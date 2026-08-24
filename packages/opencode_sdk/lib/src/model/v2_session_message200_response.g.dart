// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_message200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionMessage200Response _$V2SessionMessage200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionMessage200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['data']);
  final val = V2SessionMessage200Response(
    data: $checkedConvert('data', (v) => SessionMessage.fromJson(v)),
  );
  return val;
});

Map<String, dynamic> _$V2SessionMessage200ResponseToJson(
  V2SessionMessage200Response instance,
) => <String, dynamic>{'data': instance.data.toJson()};
