// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_events200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionEvents200Response _$V2SessionEvents200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionEvents200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['id', 'event', 'data']);
  final val = V2SessionEvents200Response(
    id: $checkedConvert('id', (v) => v as String),
    event: $checkedConvert('event', (v) => v as String),
    data: $checkedConvert('data', (v) => v as String),
  );
  return val;
});

Map<String, dynamic> _$V2SessionEvents200ResponseToJson(
  V2SessionEvents200Response instance,
) => <String, dynamic>{
  'id': instance.id,
  'event': instance.event,
  'data': instance.data,
};
