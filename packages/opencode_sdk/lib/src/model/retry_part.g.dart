// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'retry_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

RetryPart _$RetryPartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('RetryPart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'sessionID',
          'messageID',
          'type',
          'attempt',
          'error',
          'time',
        ],
      );
      final val = RetryPart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$RetryPartTypeEnumEnumMap,
            v,
            unknownValue: RetryPartTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        attempt: $checkedConvert('attempt', (v) => (v as num).toInt()),
        error: $checkedConvert(
          'error',
          (v) => APIError.fromJson(v as Map<String, dynamic>),
        ),
        time: $checkedConvert(
          'time',
          (v) => RetryPartTime.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$RetryPartToJson(RetryPart instance) => <String, dynamic>{
  'id': instance.id,
  'sessionID': instance.sessionID,
  'messageID': instance.messageID,
  'type': _$RetryPartTypeEnumEnumMap[instance.type]!,
  'attempt': instance.attempt,
  'error': instance.error.toJson(),
  'time': instance.time.toJson(),
};

const _$RetryPartTypeEnumEnumMap = {
  RetryPartTypeEnum.retry: 'retry',
  RetryPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
