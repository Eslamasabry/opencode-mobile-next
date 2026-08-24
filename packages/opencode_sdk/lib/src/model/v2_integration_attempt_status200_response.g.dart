// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_integration_attempt_status200_response.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2IntegrationAttemptStatus200Response
_$V2IntegrationAttemptStatus200ResponseFromJson(Map<String, dynamic> json) =>
    $checkedCreate('V2IntegrationAttemptStatus200Response', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['location', 'data']);
      final val = V2IntegrationAttemptStatus200Response(
        location: $checkedConvert(
          'location',
          (v) => LocationInfo.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => IntegrationAttemptStatus.fromJson(v),
        ),
      );
      return val;
    });

Map<String, dynamic> _$V2IntegrationAttemptStatus200ResponseToJson(
  V2IntegrationAttemptStatus200Response instance,
) => <String, dynamic>{
  'location': instance.location.toJson(),
  'data': instance.data.toJson(),
};
