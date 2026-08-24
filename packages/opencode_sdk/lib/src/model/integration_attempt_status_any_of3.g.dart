// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_attempt_status_any_of3.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationAttemptStatusAnyOf3 _$IntegrationAttemptStatusAnyOf3FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationAttemptStatusAnyOf3', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['status', 'time']);
  final val = IntegrationAttemptStatusAnyOf3(
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$IntegrationAttemptStatusAnyOf3StatusEnumEnumMap,
        v,
        unknownValue:
            IntegrationAttemptStatusAnyOf3StatusEnum.unknownDefaultOpenApi,
      ),
    ),
    time: $checkedConvert(
      'time',
      (v) => IntegrationAttemptStatusAnyOf3Time.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationAttemptStatusAnyOf3ToJson(
  IntegrationAttemptStatusAnyOf3 instance,
) => <String, dynamic>{
  'status': _$IntegrationAttemptStatusAnyOf3StatusEnumEnumMap[instance.status]!,
  'time': instance.time.toJson(),
};

const _$IntegrationAttemptStatusAnyOf3StatusEnumEnumMap = {
  IntegrationAttemptStatusAnyOf3StatusEnum.expired: 'expired',
  IntegrationAttemptStatusAnyOf3StatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
