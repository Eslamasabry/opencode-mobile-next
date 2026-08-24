// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_attempt_status_any_of2.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationAttemptStatusAnyOf2 _$IntegrationAttemptStatusAnyOf2FromJson(
  Map<String, dynamic> json,
) => $checkedCreate('IntegrationAttemptStatusAnyOf2', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['status', 'message', 'time']);
  final val = IntegrationAttemptStatusAnyOf2(
    status: $checkedConvert(
      'status',
      (v) => $enumDecode(
        _$IntegrationAttemptStatusAnyOf2StatusEnumEnumMap,
        v,
        unknownValue:
            IntegrationAttemptStatusAnyOf2StatusEnum.unknownDefaultOpenApi,
      ),
    ),
    message: $checkedConvert('message', (v) => v as String),
    time: $checkedConvert(
      'time',
      (v) => IntegrationAttemptStatusAnyOf2Time.fromJson(
        v as Map<String, dynamic>,
      ),
    ),
  );
  return val;
});

Map<String, dynamic> _$IntegrationAttemptStatusAnyOf2ToJson(
  IntegrationAttemptStatusAnyOf2 instance,
) => <String, dynamic>{
  'status': _$IntegrationAttemptStatusAnyOf2StatusEnumEnumMap[instance.status]!,
  'message': instance.message,
  'time': instance.time.toJson(),
};

const _$IntegrationAttemptStatusAnyOf2StatusEnumEnumMap = {
  IntegrationAttemptStatusAnyOf2StatusEnum.failed: 'failed',
  IntegrationAttemptStatusAnyOf2StatusEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
