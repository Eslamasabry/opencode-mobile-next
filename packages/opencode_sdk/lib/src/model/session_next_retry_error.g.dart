// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_next_retry_error.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionNextRetryError _$SessionNextRetryErrorFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionNextRetryError', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['message', 'isRetryable']);
  final val = SessionNextRetryError(
    message: $checkedConvert('message', (v) => v as String),
    statusCode: $checkedConvert('statusCode', (v) => v as num?),
    isRetryable: $checkedConvert('isRetryable', (v) => v as bool),
    responseHeaders: $checkedConvert(
      'responseHeaders',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as String)),
    ),
    responseBody: $checkedConvert('responseBody', (v) => v as String?),
    metadata: $checkedConvert(
      'metadata',
      (v) =>
          (v as Map<String, dynamic>?)?.map((k, e) => MapEntry(k, e as String)),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionNextRetryErrorToJson(
  SessionNextRetryError instance,
) => <String, dynamic>{
  'message': instance.message,
  'statusCode': ?instance.statusCode,
  'isRetryable': instance.isRetryable,
  'responseHeaders': ?instance.responseHeaders,
  'responseBody': ?instance.responseBody,
  'metadata': ?instance.metadata,
};
