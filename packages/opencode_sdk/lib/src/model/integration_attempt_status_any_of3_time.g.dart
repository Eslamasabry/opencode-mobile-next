// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_attempt_status_any_of3_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationAttemptStatusAnyOf3Time _$IntegrationAttemptStatusAnyOf3TimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationAttemptStatusAnyOf3Time', json, (
  $checkedConvert,
) {
  $checkKeys(json, requiredKeys: const ['created', 'expires']);
  final val = IntegrationAttemptStatusAnyOf3Time(
    created: $checkedConvert(
      'created',
      (v) => OpencodeSdkRawUnion032.fromJson(v),
    ),
    expires: $checkedConvert(
      'expires',
      (v) => OpencodeSdkRawUnion033.fromJson(v),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationAttemptStatusAnyOf3TimeToJson(
  IntegrationAttemptStatusAnyOf3Time instance,
) => <String, dynamic>{
  'created': instance.created.toJson(),
  'expires': instance.expires.toJson(),
};
