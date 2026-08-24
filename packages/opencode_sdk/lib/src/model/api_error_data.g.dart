// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'api_error_data.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

APIErrorData _$APIErrorDataFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('APIErrorData', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['message', 'isRetryable']);
  final val = APIErrorData(
    message: $checkedConvert('message', (v) => v as String),
    statusCode: $checkedConvert('statusCode', (v) => (v as num?)?.toInt()),
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

Map<String, dynamic> _$APIErrorDataToJson(APIErrorData instance) =>
    <String, dynamic>{
      'message': instance.message,
      'statusCode': ?instance.statusCode,
      'isRetryable': instance.isRetryable,
      'responseHeaders': ?instance.responseHeaders,
      'responseBody': ?instance.responseBody,
      'metadata': ?instance.metadata,
    };
