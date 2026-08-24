// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_integration_get200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2IntegrationGet200Response _$V2IntegrationGet200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2IntegrationGet200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['location', 'data']);
  final val = V2IntegrationGet200Response(
    location: $checkedConvert(
      'location',
      (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => IntegrationInfo.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2IntegrationGet200ResponseToJson(
  V2IntegrationGet200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.toJson(),
};
