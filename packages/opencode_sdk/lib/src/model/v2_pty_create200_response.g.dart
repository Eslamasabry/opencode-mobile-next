// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_pty_create200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2PtyCreate200Response _$V2PtyCreate200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2PtyCreate200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['location', 'data']);
  final val = V2PtyCreate200Response(
    location: $checkedConvert(
      'location',
      (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => Pty.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2PtyCreate200ResponseToJson(
  V2PtyCreate200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.toJson(),
};
