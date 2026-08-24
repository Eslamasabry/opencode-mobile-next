// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_attempt_status_any_of1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationAttemptStatusAnyOf1 _$IntegrationAttemptStatusAnyOf1FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationAttemptStatusAnyOf1', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['status', 'time']);
  final val = IntegrationAttemptStatusAnyOf1(
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$IntegrationAttemptStatusAnyOf1StatusEnumEnumMap,
        v,
        unknownValue:
            IntegrationAttemptStatusAnyOf1StatusEnum.unknownDefaultOpenApi,
      ),
    ),
    time: $checkedConvert(
      'time',
      (v) => IntegrationAttemptStatusAnyOf1Time.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationAttemptStatusAnyOf1ToJson(
  IntegrationAttemptStatusAnyOf1 instance,
) => <String, dynamic>{
  'status': _$IntegrationAttemptStatusAnyOf1StatusEnumEnumMap[instance.status]!,
  'time': instance.time.toJson(),
};

const _$IntegrationAttemptStatusAnyOf1StatusEnumEnumMap = {
  IntegrationAttemptStatusAnyOf1StatusEnum.complete: 'complete',
  IntegrationAttemptStatusAnyOf1StatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
