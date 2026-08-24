// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_attempt_status_any_of_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationAttemptStatusAnyOfTime _$IntegrationAttemptStatusAnyOfTimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationAttemptStatusAnyOfTime', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['created', 'expires']);
  final val = IntegrationAttemptStatusAnyOfTime(
    created: $checkedConvert(
      'created',
      (v) => OpencodeSdkRawUnion025.fromJson(v),
    ),
    expires: $checkedConvert(
      'expires',
      (v) => OpencodeSdkRawUnion026.fromJson(v),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationAttemptStatusAnyOfTimeToJson(
  IntegrationAttemptStatusAnyOfTime instance,
) => <String, dynamic>{
  'created': instance.created.toJson(),
  'expires': instance.expires.toJson(),
};
