// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_status_any_of1.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionStatusAnyOf1 _$SessionStatusAnyOf1FromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionStatusAnyOf1', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['type', 'attempt', 'message', 'next'],
      );
      final val = SessionStatusAnyOf1(
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionStatusAnyOf1TypeEnumEnumMap,
            v,
            unknownValue: SessionStatusAnyOf1TypeEnum.unknownDefaultOpenApi,
          ),
        ),
        attempt: $checkedConvert('attempt', (v) => (v as num).toInt()),
        message: $checkedConvert('message', (v) => v as String),
        action: $checkedConvert(
          'action',
          (v) => v == null
              ? null
              : SessionStatusAnyOf1Action.fromJson(v as Map<String, dynamic>),
        ),
        next: $checkedConvert('next', (v) => (v as num).toInt()),
      );
      return val;
    });

Map<String, dynamic> _$SessionStatusAnyOf1ToJson(
  SessionStatusAnyOf1 instance,
) => <String, dynamic>{
  'type': _$SessionStatusAnyOf1TypeEnumEnumMap[instance.type]!,
  'attempt': instance.attempt,
  'message': instance.message,
  'action': ?instance.action?.toJson(),
  'next': instance.next,
};

const _$SessionStatusAnyOf1TypeEnumEnumMap = {
  SessionStatusAnyOf1TypeEnum.retry: 'retry',
  SessionStatusAnyOf1TypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
