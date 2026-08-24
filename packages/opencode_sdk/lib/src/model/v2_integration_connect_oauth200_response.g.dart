// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_integration_connect_oauth200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2IntegrationConnectOauth200Response
_$V2IntegrationConnectOauth200ResponseFromJson(Map<String, dynamic> json) =>
    $checkedCreate('V2IntegrationConnectOauth200Response', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['location', 'data']);
      final val = V2IntegrationConnectOauth200Response(
        location: $checkedConvert(
          'location',
          (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => IntegrationAttempt.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$V2IntegrationConnectOauth200ResponseToJson(
  V2IntegrationConnectOauth200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.toJson(),
};
