// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionError _$SessionErrorFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionError', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = SessionError(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SessionErrorTypeEnumEnumMap,
            v,
            unknownValue: SessionErrorTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        durable: $checkedConvert(
          'durable',
          (v) => v == null
              ? null
              : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
        ),
        location: $checkedConvert(
          'location',
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) => SessionErrorData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SessionErrorToJson(SessionError instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$SessionErrorTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$SessionErrorTypeEnumEnumMap = {
  SessionErrorTypeEnum.sessionPeriodError: 'session.error',
  SessionErrorTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};
