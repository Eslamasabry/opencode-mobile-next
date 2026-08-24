// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_integration_list200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2IntegrationList200Response _$V2IntegrationList200ResponseFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2IntegrationList200Response', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['location', 'data']);
  final val = V2IntegrationList200Response(
    location: $checkedConvert(
      'location',
      (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
    ),
    data: $checkedConvert(
      'data',
      (v) => (v as List<dynamic>)
          .map((e) => IntegrationInfo.fromJson(e as Map<String, dynamic>))
          .toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$V2IntegrationList200ResponseToJson(
  V2IntegrationList200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.map((e) => e.toJson()).toList(),
};
