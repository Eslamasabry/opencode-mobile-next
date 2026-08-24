// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_get200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionGet200Response _$V2SessionGet200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionGet200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['data']);
  final val = V2SessionGet200Response(
    data: $checkedConvert(
      'data',
      (v) => SessionV2Info.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SessionGet200ResponseToJson(
  V2SessionGet200Response instance,
) => <String, dynamic>{'data': instance.data.toJson()};
