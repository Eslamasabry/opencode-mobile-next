// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_attempt_status_any_of.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationAttemptStatusAnyOf _$IntegrationAttemptStatusAnyOfFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationAttemptStatusAnyOf', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['status', 'time']);
  final val = IntegrationAttemptStatusAnyOf(
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$IntegrationAttemptStatusAnyOfStatusEnumEnumMap,
        v,
        unknownValue:
            IntegrationAttemptStatusAnyOfStatusEnum.unknownDefaultOpenApi,
      ),
    ),
    time: $checkedConvert(
      'time',
      (v) =>
          IntegrationAttemptStatusAnyOfTime.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationAttemptStatusAnyOfToJson(
  IntegrationAttemptStatusAnyOf instance,
) => <String, dynamic>{
  'status': _$IntegrationAttemptStatusAnyOfStatusEnumEnumMap[instance.status]!,
  'time': instance.time.toJson(),
};

const _$IntegrationAttemptStatusAnyOfStatusEnumEnumMap = {
  IntegrationAttemptStatusAnyOfStatusEnum.pending: 'pending',
  IntegrationAttemptStatusAnyOfStatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
