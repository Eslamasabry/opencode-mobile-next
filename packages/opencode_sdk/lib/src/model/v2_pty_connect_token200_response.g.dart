// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_pty_connect_token200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2PtyConnectToken200Response _$V2PtyConnectToken200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2PtyConnectToken200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['location', 'data']);
  final val = V2PtyConnectToken200Response(
    location: $checkedConvert(
      'location',
      (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => PtyTicketConnectToken.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2PtyConnectToken200ResponseToJson(
  V2PtyConnectToken200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.toJson(),
};
