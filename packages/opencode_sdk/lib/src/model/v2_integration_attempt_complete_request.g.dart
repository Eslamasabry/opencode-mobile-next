// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_integration_attempt_complete_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2IntegrationAttemptCompleteRequest
_$V2IntegrationAttemptCompleteRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('V2IntegrationAttemptCompleteRequest', json, (
      $checkedConvert,
    ) {
      final val = V2IntegrationAttemptCompleteRequest(
        code: $checkedConvert('code', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$V2IntegrationAttemptCompleteRequestToJson(
  V2IntegrationAttemptCompleteRequest instance,
) => <String, dynamic>{'code': ?instance.code};
