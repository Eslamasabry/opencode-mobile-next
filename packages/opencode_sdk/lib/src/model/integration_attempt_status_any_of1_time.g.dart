// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_attempt_status_any_of1_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationAttemptStatusAnyOf1Time _$IntegrationAttemptStatusAnyOf1TimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationAttemptStatusAnyOf1Time', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['created', 'expires']);
  final val = IntegrationAttemptStatusAnyOf1Time(
    created: $checkedConvert(
      'created',
      (v) => OpencodeSdkRawUnion028.fromJson(v),
    ),
    expires: $checkedConvert(
      'expires',
      (v) => OpencodeSdkRawUnion029.fromJson(v),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationAttemptStatusAnyOf1TimeToJson(
  IntegrationAttemptStatusAnyOf1Time instance,
) => <String, dynamic>{
  'created': instance.created.toJson(),
  'expires': instance.expires.toJson(),
};
