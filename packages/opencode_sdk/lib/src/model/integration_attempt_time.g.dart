// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_attempt_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationAttemptTime _$IntegrationAttemptTimeFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationAttemptTime', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['created', 'expires']);
  final val = IntegrationAttemptTime(
    created: $checkedConvert(
      'created',
      (v) => OpencodeSdkRawUnion024.fromJson(v),
    ),
    expires: $checkedConvert(
      'expires',
      (v) => OpencodeSdkRawUnion025.fromJson(v),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationAttemptTimeToJson(
  IntegrationAttemptTime instance,
) => <String, dynamic>{
  'created': instance.created.toJson(),
  'expires': instance.expires.toJson(),
};
