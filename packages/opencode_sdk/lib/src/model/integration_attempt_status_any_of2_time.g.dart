// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_attempt_status_any_of2_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationAttemptStatusAnyOf2Time _$IntegrationAttemptStatusAnyOf2TimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationAttemptStatusAnyOf2Time', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['created', 'expires']);
  final val = IntegrationAttemptStatusAnyOf2Time(
    created: $checkedConvert(
      'created',
      (v) => OpencodeSdkRawUnion029.fromJson(v),
    ),
    expires: $checkedConvert(
      'expires',
      (v) => OpencodeSdkRawUnion030.fromJson(v),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationAttemptStatusAnyOf2TimeToJson(
  IntegrationAttemptStatusAnyOf2Time instance,
) => <String, dynamic>{
  'created': instance.created.toJson(),
  'expires': instance.expires.toJson(),
};
