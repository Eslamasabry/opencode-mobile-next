// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_create200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionCreate200Response _$V2SessionCreate200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionCreate200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['data']);
  final val = V2SessionCreate200Response(
    data: $checkedConvert(
      'data',
      (v) => SessionV2Info.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2SessionCreate200ResponseToJson(
  V2SessionCreate200Response instance,
) => <String, dynamic>{'data': instance.data.toJson()};
