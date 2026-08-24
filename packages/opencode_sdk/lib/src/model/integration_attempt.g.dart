// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'integration_attempt.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

IntegrationAttempt _$IntegrationAttemptFromJson(Map<String, dynamic> json) =>
    $checkedCreate('IntegrationAttempt', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'attemptID',
          'url',
          'instructions',
          'mode',
          'time',
        ],
      );
      final val = IntegrationAttempt(
        attemptID: $checkedConvert('attemptID', (v) => v as String),
        url: $checkedConvert('url', (v) => v as String),
        instructions: $checkedConvert('instructions', (v) => v as String),
        mode: $checkedConvert(
          'mode',
          (v) => $enumDecode(
            _$IntegrationAttemptModeEnumEnumMap,
            v,
            unknownValue: IntegrationAttemptModeEnum.unknownDefaultOpenApi,
          ),
        ),
        time: $checkedConvert(
          'time',
          (v) => IntegrationAttemptTime.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$IntegrationAttemptToJson(IntegrationAttempt instance) =>
    <String, dynamic>{
      'attemptID': instance.attemptID,
      'url': instance.url,
      'instructions': instance.instructions,
      'mode': _$IntegrationAttemptModeEnumEnumMap[instance.mode]!,
      'time': instance.time.toJson(),
    };

const _$IntegrationAttemptModeEnumEnumMap = {
  IntegrationAttemptModeEnum.auto: 'auto',
  IntegrationAttemptModeEnum.code: 'code',
  IntegrationAttemptModeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
