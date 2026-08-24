// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_pty_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2PtyList200Response _$V2PtyList200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2PtyList200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['location', 'data']);
  final val = V2PtyList200Response(
    location: $checkedConvert(
      'location',
      (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => (v as List<dynamic>)
          .map((e) => Pty.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2PtyList200ResponseToJson(
  V2PtyList200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.map((e) => e.toJson()).toList(),
};
